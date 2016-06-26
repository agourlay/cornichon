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
import io.circe.Json

import scala.annotation.tailrec
import scala.concurrent.duration._

class HttpService(baseUrl: String, requestTimeout: FiniteDuration, client: HttpClient, resolver: Resolver) {

  import com.github.agourlay.cornichon.http.HttpService._

  def resolveRequestParts(url: String, body: Option[String], params: Seq[(String, String)], headers: Seq[(String, String)])(s: Session) =
    for {
      jsonBodyResolved ← body.fold[Xor[CornichonError, Option[Json]]](right(None)) { p ⇒
        resolver.fillPlaceholders(p)(s)
          .flatMap(parseJson)
          .map(Some(_))
      }
      urlResolved ← resolver.fillPlaceholders(withBaseUrl(url))(s)
      paramsResolved ← resolveParams(url, params)(s)
      headersResolved ← resolver.tuplesResolver(headers, s)
      parsedHeaders ← parseHttpHeaders(headersResolved)
      extractedHeaders ← extractWithHeadersSession(s)
    } yield (urlResolved, jsonBodyResolved, paramsResolved, parsedHeaders ++ extractedHeaders)

  def runRequest(r: HttpRequest, expectedStatus: Option[Int] = None, extractor: ResponseExtractor = NoOpExtraction)(s: Session) =
    for {
      parts ← resolveRequestParts(r.url, r.body, r.params, r.headers)(s)
      resp ← client.runRequest(r.method, parts._1, parts._2, parts._3, parts._4, requestTimeout)
      newSession ← handleResponse(resp, expectedStatus, extractor)(s)
    } yield (resp, newSession)

  def runStreamRequest(r: HttpStreamedRequest, expectedStatus: Option[Int], extractor: ResponseExtractor)(s: Session) =
    for {
      parts ← resolveRequestParts(r.url, None, r.params, r.headers)(s)
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
    def loop(headers: Seq[(String, String)], acc: Seq[HttpHeader]): Xor[MalformedHeadersError, Seq[HttpHeader]] =
      if (headers.isEmpty) right(acc)
      else {
        val (name, value) = headers.head
        HttpHeader.parse(name, value) match {
          case ParsingResult.Ok(h, e) ⇒ loop(headers.tail, acc :+ h)
          case ParsingResult.Error(e) ⇒ left(MalformedHeadersError(e.formatPretty))
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

  def requestEffect(request: HttpRequest, extractor: ResponseExtractor = NoOpExtraction, expectedStatus: Option[Int] = None): Session ⇒ Session =
    s ⇒ runRequest(request, expectedStatus, extractor)(s).fold(e ⇒ throw e, _._2)

  def post(url: String, body: Option[String], params: Seq[(String, String)], headers: Seq[(String, String)],
    extractor: ResponseExtractor = NoOpExtraction, expectedStatus: Option[Int] = None) = {
    val req = HttpRequest(POST, url, body, params, headers)
    requestEffect(req, extractor, expectedStatus)
  }

  def put(url: String, body: Option[String], params: Seq[(String, String)], headers: Seq[(String, String)],
    extractor: ResponseExtractor = NoOpExtraction, expectedStatus: Option[Int] = None) = {
    val req = HttpRequest(PUT, url, body, params, headers)
    requestEffect(req, extractor, expectedStatus)
  }

  def patch(url: String, body: Option[String], params: Seq[(String, String)], headers: Seq[(String, String)],
    extractor: ResponseExtractor = NoOpExtraction, expectedStatus: Option[Int] = None) = {
    val req = HttpRequest(PATCH, url, body, params, headers)
    requestEffect(req, extractor, expectedStatus)
  }

  def get(url: String, body: Option[String] = None, params: Seq[(String, String)], headers: Seq[(String, String)],
    extractor: ResponseExtractor = NoOpExtraction, expectedStatus: Option[Int] = None) = {
    val req = HttpRequest(GET, url, body, params, headers)
    requestEffect(req, extractor, expectedStatus)
  }

  def head(url: String, body: Option[String] = None, params: Seq[(String, String)], headers: Seq[(String, String)],
    extractor: ResponseExtractor = NoOpExtraction, expectedStatus: Option[Int] = None) = {
    val req = HttpRequest(HEAD, url, body, params, headers)
    requestEffect(req, extractor, expectedStatus)
  }

  def options(url: String, body: Option[String] = None, params: Seq[(String, String)], headers: Seq[(String, String)],
    extractor: ResponseExtractor = NoOpExtraction, expectedStatus: Option[Int] = None) = {
    val req = HttpRequest(OPTIONS, url, body, params, headers)
    requestEffect(req, extractor, expectedStatus)
  }

  def delete(url: String, body: Option[String] = None, params: Seq[(String, String)], headers: Seq[(String, String)],
    extractor: ResponseExtractor = NoOpExtraction, expectedStatus: Option[Int] = None) = {
    val req = HttpRequest(DELETE, url, body, params, headers)
    requestEffect(req, extractor, expectedStatus)
  }

  def streamEffect(request: HttpStreamedRequest, expectedStatus: Option[Int] = None, extractor: ResponseExtractor = NoOpExtraction): Session ⇒ Session =
    s ⇒ runStreamRequest(request, expectedStatus, extractor)(s).fold(e ⇒ throw e, _._2)

  def openSSE(url: String, takeWithin: FiniteDuration, params: Seq[(String, String)], headers: Seq[(String, String)],
    extractor: ResponseExtractor = NoOpExtraction, expectedStatus: Option[Int] = None) = {
    val req = HttpStreamedRequest(SSE, url, takeWithin, params, headers)
    streamEffect(req, expectedStatus, extractor)
  }

  def openWS(url: String, takeWithin: FiniteDuration, params: Seq[(String, String)], headers: Seq[(String, String)],
    extractor: ResponseExtractor = NoOpExtraction, expectedStatus: Option[Int] = None) = {
    val req = HttpStreamedRequest(WS, url, takeWithin, params, headers)
    streamEffect(req, expectedStatus, extractor)
  }
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

  def decodeSessionHeaders(headers: String): Seq[(String, String)] =
    headers.split(InterHeadersValueDelim).toSeq.map { header ⇒
      val elms = header.split(HeadersKeyValueDelim)
      (elms.head, elms.tail.head)
    }
}