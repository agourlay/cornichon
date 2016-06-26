package com.github.agourlay.cornichon.http

import akka.http.scaladsl.model.{ HttpHeader, StatusCode }
import akka.http.scaladsl.model.HttpHeader.ParsingResult
import akka.http.scaladsl.model.Uri.Query
import cats.data.Xor
import cats.data.Xor.{ left, right }
import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.http.client.HttpClient
import com.github.agourlay.cornichon.json.JsonPath
import com.github.agourlay.cornichon.json.CornichonJson._
import com.github.agourlay.cornichon.http.HttpMethods._
import com.github.agourlay.cornichon.http.HttpStreams._
import com.github.agourlay.cornichon.steps.regular.EffectStep
import io.circe.Json
import sangria.renderer.QueryRenderer

import scala.annotation.tailrec
import scala.concurrent.duration._

class HttpService(baseUrl: String, requestTimeout: FiniteDuration, client: HttpClient, resolver: Resolver) {

  import com.github.agourlay.cornichon.http.HttpService._

  def resolveRequestParts(url: String, payload: Option[String], params: Seq[(String, String)], headers: Seq[(String, String)])(s: Session, resolver: Resolver) =
    for {
      payload ← payload.fold[Xor[CornichonError, Option[Json]]](right(None)) { p ⇒
        resolver.fillPlaceholders(p)(s)
          .flatMap(parseJson)
          .map(Some(_))
      }
      urlResolved ← resolver.fillPlaceholders(withBaseUrl(url))(s)
      paramsResolved ← resolveParams(url, params)(s)
      headersResolved ← resolver.tuplesResolver(headers, s)
      parsedHeaders ← parseHttpHeaders(headersResolved)
      extractedHeaders ← extractWithHeadersSession(s)
    } yield (urlResolved, payload, paramsResolved, parsedHeaders ++ extractedHeaders)

  def runRequest(r: HttpRequest, expectedStatus: Option[Int] = None, extractor: ResponseExtractor = NoOpExtraction)(s: Session) =
    for {
      parts ← resolveRequestParts(r.url, r.payload, r.params, r.headers)(s, resolver)
      resp ← client.runRequest(r.method, parts._1, parts._2, parts._3, parts._4, requestTimeout)
      newSession ← handleResponse(resp, expectedStatus, extractor)(s)
    } yield (resp, newSession)

  def runStreamRequest(r: HttpStreamedRequest, expectedStatus: Option[Int], extractor: ResponseExtractor)(s: Session) =
    for {
      parts ← resolveRequestParts(r.url, None, r.params, r.headers)(s, resolver)
      resp ← client.openStream(r.stream, parts._1, parts._3, parts._4, r.takeWithin)
      newSession ← handleResponse(resp, expectedStatus, extractor)(s)
    } yield (resp, newSession)

  def expectStatusCode(httpResponse: CornichonHttpResponse, expected: Option[Int]): Xor[CornichonError, CornichonHttpResponse] =
    expected.map { expectedStatus ⇒
      if (httpResponse.status == StatusCode.int2StatusCode(expectedStatus))
        right(httpResponse)
      else
        left(StatusNonExpected(expectedStatus, httpResponse))
    }.getOrElse(right(httpResponse))

  def resolveParams(url: String, params: Seq[(String, String)])(session: Session): Xor[CornichonError, Seq[(String, String)]] = {
    val urlsParamsPart = url.dropWhile(_ != '?').drop(1)
    val urlParams = if (urlsParamsPart.trim.isEmpty) Map.empty else Query.apply(urlsParamsPart).toMap
    resolver.tuplesResolver(urlParams.toSeq ++ params, session)
  }

  def handleResponse(resp: CornichonHttpResponse, expectedStatus: Option[Int], extractor: ResponseExtractor)(session: Session) =
    for {
      resExpected ← expectStatusCode(resp, expectedStatus)
      newSession ← fillInSessionWithResponse(session, resp, extractor)
    } yield newSession

  def commonSessionExtraction(session: Session, response: CornichonHttpResponse): Session =
    session.addValues(Seq(
      LastResponseStatusKey → response.status.intValue().toString,
      LastResponseBodyKey → response.body,
      LastResponseHeadersKey → encodeSessionHeaders(response)
    ))

  def fillInSessionWithResponse(session: Session, response: CornichonHttpResponse, extractor: ResponseExtractor): Xor[CornichonError, Session] =
    extractor match {
      case NoOpExtraction ⇒
        right(commonSessionExtraction(session, response))

      case RootExtractor(targetKey) ⇒
        right(commonSessionExtraction(session, response).addValue(targetKey, response.body))

      case PathExtractor(path, targetKey) ⇒
        JsonPath.run(path, response.body)
          .map { extractedJson ⇒
            commonSessionExtraction(session, response).addValue(targetKey, jsonStringValue(extractedJson))
          }
    }

  def parseHttpHeaders(headers: Seq[(String, String)]): Xor[MalformedHeadersError, Seq[HttpHeader]] = {
    @tailrec
    def loop(headers: Seq[(String, String)], acc: Seq[HttpHeader]): Xor[MalformedHeadersError, Seq[HttpHeader]] = {
      if (headers.isEmpty) right(acc)
      else {
        val (name, value) = headers.head
        HttpHeader.parse(name, value) match {
          case ParsingResult.Ok(h, e) ⇒ loop(headers.tail, acc :+ h)
          case ParsingResult.Error(e) ⇒ left(MalformedHeadersError(e.formatPretty))
        }
      }
    }
    loop(headers, Seq.empty[HttpHeader])
  }

  def extractWithHeadersSession(session: Session): Xor[MalformedHeadersError, Seq[HttpHeader]] =
    session.getOpt(WithHeadersKey).fold[Xor[MalformedHeadersError, Seq[HttpHeader]]](right(Seq.empty[HttpHeader])) { headers ⇒
      val tuples: Seq[(String, String)] = decodeSessionHeaders(headers)
      parseHttpHeaders(tuples)
    }

  private def withBaseUrl(input: String) = if (baseUrl.isEmpty) input else baseUrl + input

  def requestEffect(request: HttpRequest, extractor: ResponseExtractor = NoOpExtraction, expectedStatus: Option[Int] = None): EffectStep =
    EffectStep(
      title = request.description,
      effect = s ⇒ runRequest(request, expectedStatus, extractor)(s).fold(e ⇒ throw e, _._2)
    )

  def Post(url: String, payload: Option[String], params: Seq[(String, String)], headers: Seq[(String, String)],
    extractor: ResponseExtractor = NoOpExtraction, expectedStatus: Option[Int] = None)(s: Session) = {
    val req = HttpRequest(POST, url, payload, params, headers)
    requestEffect(req, extractor, expectedStatus)
  }

  def Put(url: String, payload: Option[String], params: Seq[(String, String)], headers: Seq[(String, String)],
    extractor: ResponseExtractor = NoOpExtraction, expectedStatus: Option[Int] = None)(s: Session) = {
    val req = HttpRequest(POST, url, payload, params, headers)
    requestEffect(req, extractor, expectedStatus)
  }

  def Patch(url: String, payload: Option[String], params: Seq[(String, String)], headers: Seq[(String, String)],
    extractor: ResponseExtractor = NoOpExtraction, expectedStatus: Option[Int] = None)(s: Session) = {
    val req = HttpRequest(POST, url, payload, params, headers)
    requestEffect(req, extractor, expectedStatus)
  }

  def Get(url: String, payload: Option[String] = None, params: Seq[(String, String)], headers: Seq[(String, String)],
    extractor: ResponseExtractor = NoOpExtraction, expectedStatus: Option[Int] = None)(s: Session) = {
    val req = HttpRequest(POST, url, payload, params, headers)
    requestEffect(req, extractor, expectedStatus)
  }

  def Head(url: String, payload: Option[String] = None, params: Seq[(String, String)], headers: Seq[(String, String)],
    extractor: ResponseExtractor = NoOpExtraction, expectedStatus: Option[Int] = None)(s: Session) = {
    val req = HttpRequest(POST, url, payload, params, headers)
    requestEffect(req, extractor, expectedStatus)
  }

  def Options(url: String, payload: Option[String] = None, params: Seq[(String, String)], headers: Seq[(String, String)],
    extractor: ResponseExtractor = NoOpExtraction, expectedStatus: Option[Int] = None)(s: Session) = {
    val req = HttpRequest(POST, url, payload, params, headers)
    requestEffect(req, extractor, expectedStatus)
  }

  def Delete(url: String, payload: Option[String] = None, params: Seq[(String, String)], headers: Seq[(String, String)],
    extractor: ResponseExtractor = NoOpExtraction, expectedStatus: Option[Int] = None)(s: Session) = {
    val req = HttpRequest(POST, url, payload, params, headers)
    requestEffect(req, extractor, expectedStatus)
  }

  def streamEffect(request: HttpStreamedRequest, expectedStatus: Option[Int] = None, extractor: ResponseExtractor = NoOpExtraction): EffectStep =
    EffectStep(
      title = request.description,
      effect = s ⇒ runStreamRequest(request, expectedStatus, extractor)(s).fold(e ⇒ throw e, _._2)
    )

  def OpenSSE(url: String, takeWithin: FiniteDuration, params: Seq[(String, String)], headers: Seq[(String, String)],
    extractor: ResponseExtractor = NoOpExtraction, expectedStatus: Option[Int] = None)(s: Session) = {
    val req = HttpStreamedRequest(SSE, url, takeWithin, params, headers)
    streamEffect(req, expectedStatus, extractor)
  }

  def OpenWS(url: String, takeWithin: FiniteDuration, params: Seq[(String, String)], headers: Seq[(String, String)],
    extractor: ResponseExtractor = NoOpExtraction, expectedStatus: Option[Int] = None)(s: Session) = {
    val req = HttpStreamedRequest(WS, url, takeWithin, params, headers)
    streamEffect(req, expectedStatus, extractor)
  }

  def queryGqlEffect(queryGQL: QueryGQL, expectedStatus: Option[Int] = None, extractor: ResponseExtractor = NoOpExtraction) = {

    // Used only for display - problem being that the query is a String and looks ugly inside the full JSON object.
    val payload = queryGQL.query.source.getOrElse(QueryRenderer.render(queryGQL.query, QueryRenderer.Pretty))

    val fullPayload = {
      import io.circe.generic.auto._
      import io.circe.syntax._

      val gqlPayload = GqlPayload(payload, queryGQL.operationName, queryGQL.variables)
      prettyPrint(gqlPayload.asJson)
    }

    val httpRequest = HttpRequest(POST, queryGQL.url, Some(fullPayload), Seq.empty, Seq.empty)

    EffectStep(
      title = s"POST to ${queryGQL.url} the GraphQL query $payload",
      effect = s ⇒ runRequest(httpRequest, expectedStatus, extractor)(s).fold(e ⇒ throw e, _._2)
    )
  }

  private case class GqlPayload(query: String, operationName: Option[String], variables: Option[Map[String, Json]])

}

sealed trait ResponseExtractor
case class RootExtractor(targetKey: String) extends ResponseExtractor
case class PathExtractor(path: String, targetKey: String) extends ResponseExtractor
object NoOpExtraction extends ResponseExtractor

object HttpService {
  val LastResponseBodyKey = "last-response-body"
  val LastResponseStatusKey = "last-response-status"
  val LastResponseHeadersKey = "last-response-headers"
  val WithHeadersKey = "with-headers"
  val HeadersKeyValueDelim = '|'
  val InterHeadersValueDelim = ";"

  def encodeSessionHeaders(response: CornichonHttpResponse): String =
    response.headers.map { h ⇒
      s"${h.name()}$HeadersKeyValueDelim${h.value()}"
    }.mkString(InterHeadersValueDelim)

  def decodeSessionHeaders(headers: String): Seq[(String, String)] = {
    val tuples = headers.split(InterHeadersValueDelim).toSeq.map { header ⇒
      val elms = header.split(HeadersKeyValueDelim)
      (elms.head, elms.tail.head)
    }
    tuples
  }
}