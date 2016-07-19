package com.github.agourlay.cornichon.http

import java.util.concurrent.TimeoutException

import cats.data.Xor
import cats.data.Xor.{ left, right }

import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.http.client.HttpClient
import com.github.agourlay.cornichon.json.JsonPath
import com.github.agourlay.cornichon.json.CornichonJson._
import com.github.agourlay.cornichon.http.HttpMethods._
import com.github.agourlay.cornichon.http.HttpStreams._

import scala.concurrent.{ Await, Future }
import scala.concurrent.duration._
import scala.util.{ Failure, Success, Try }

class HttpService(baseUrl: String, requestTimeout: FiniteDuration, client: HttpClient, resolver: Resolver) {

  import com.github.agourlay.cornichon.http.HttpService._
  import com.github.agourlay.cornichon.core.Input._

  def resolveRequestParts[A: Input](url: String, body: Option[A], params: Seq[(String, String)], headers: Seq[(String, String)])(s: Session) = {
    val ri = implicitly[Input[A]]
    for {
      bodyResolved ← {
        body.map { b ⇒
          val resolvableForm = ri.toResolvableForm(b)
          resolver.fillPlaceholders(resolvableForm)(s).map(Some(_))
        }.getOrElse(right(None))
      }
      jsonBodyResolved ← {
        bodyResolved.map { br ⇒
          val initialType = ri.fromResolvableForm(br)
          parseJson(initialType).map(Some(_))
        }.getOrElse(right(None))
      }
      urlResolved ← resolver.fillPlaceholders(withBaseUrl(url))(s)
      paramsResolved ← resolveParams(url, params)(s)
      headersResolved ← resolver.tuplesResolver(headers, s)
    } yield (urlResolved, jsonBodyResolved, paramsResolved, headersResolved ++ extractWithHeadersSession(s))
  }

  def runRequest[A: Input](r: HttpRequest[A], expectedStatus: Option[Int] = None, extractor: ResponseExtractor = NoOpExtraction)(s: Session) =
    for {
      parts ← resolveRequestParts(r.url, r.body, r.params, r.headers)(s)
      resp ← waitForRequestFuture(parts._1, requestTimeout) {
        client.runRequest(r.method, parts._1, parts._2, parts._3, parts._4)
      }
      newSession ← handleResponse(resp, expectedStatus, extractor)(s)
    } yield (resp, newSession)

  def runStreamRequest(r: HttpStreamedRequest, expectedStatus: Option[Int], extractor: ResponseExtractor)(s: Session) =
    for {
      parts ← resolveRequestParts[String](r.url, None, r.params, r.headers)(s)
      resp ← waitForRequestFuture(parts._1, r.takeWithin) {
        client.openStream(r.stream, parts._1, parts._3, parts._4, r.takeWithin)
      }
      newSession ← handleResponse(resp, expectedStatus, extractor)(s)
    } yield (resp, newSession)

  def expectStatusCode(httpResponse: CornichonHttpResponse, expected: Option[Int]): Xor[CornichonError, CornichonHttpResponse] =
    expected.map { expectedStatus ⇒
      if (httpResponse.status == expectedStatus)
        right(httpResponse)
      else
        left(StatusNonExpected(expectedStatus, httpResponse))
    }.getOrElse(right(httpResponse))

  def resolveParams(url: String, params: Seq[(String, String)])(session: Session): Xor[CornichonError, Seq[(String, String)]] = {
    val urlsParamsPart = url.dropWhile(_ != '?').drop(1)
    val urlParams = if (urlsParamsPart.trim.isEmpty) Seq.empty else client.paramsFromUrl(urlsParamsPart)
    resolver.tuplesResolver(urlParams ++ params, session)
  }

  def handleResponse(resp: CornichonHttpResponse, expectedStatus: Option[Int], extractor: ResponseExtractor)(session: Session) =
    for {
      resExpected ← expectStatusCode(resp, expectedStatus)
      newSession ← fillInSessionWithResponse(session, resExpected, extractor)
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

  def extractWithHeadersSession(session: Session): Seq[(String, String)] =
    session.getOpt(WithHeadersKey).fold(Seq.empty[(String, String)]) { headers ⇒
      decodeSessionHeaders(headers)
    }

  private def withBaseUrl(input: String) = if (baseUrl.isEmpty) input else baseUrl + input

  private def waitForRequestFuture(initialRequestUrl: String, t: FiniteDuration)(f: Future[Xor[CornichonError, CornichonHttpResponse]]): Xor[CornichonError, CornichonHttpResponse] =
    Try { Await.result(f, t) } match {
      case Success(s) ⇒ s
      case Failure(failure) ⇒ failure match {
        case e: TimeoutException ⇒ left(TimeoutError(e.getMessage, initialRequestUrl))
        case t: Throwable        ⇒ left(RequestError(t, initialRequestUrl))
      }
    }

  def requestEffect[A: Input](request: HttpRequest[A], extractor: ResponseExtractor = NoOpExtraction, expectedStatus: Option[Int] = None): Session ⇒ Session =
    s ⇒ runRequest(request, expectedStatus, extractor)(s).fold(e ⇒ throw e, _._2)

  def post[A: Input](url: String, body: Option[A], params: Seq[(String, String)], headers: Seq[(String, String)],
    extractor: ResponseExtractor = NoOpExtraction, expectedStatus: Option[Int] = None) = {
    val req = HttpRequest(POST, url, body, params, headers)
    requestEffect(req, extractor, expectedStatus)
  }

  def put[A: Input](url: String, body: Option[A], params: Seq[(String, String)], headers: Seq[(String, String)],
    extractor: ResponseExtractor = NoOpExtraction, expectedStatus: Option[Int] = None) = {
    val req = HttpRequest(PUT, url, body, params, headers)
    requestEffect(req, extractor, expectedStatus)
  }

  def patch[A: Input](url: String, body: Option[A], params: Seq[(String, String)], headers: Seq[(String, String)],
    extractor: ResponseExtractor = NoOpExtraction, expectedStatus: Option[Int] = None) = {
    val req = HttpRequest(PATCH, url, body, params, headers)
    requestEffect(req, extractor, expectedStatus)
  }

  def get[A: Input](url: String, body: Option[A] = None, params: Seq[(String, String)], headers: Seq[(String, String)],
    extractor: ResponseExtractor = NoOpExtraction, expectedStatus: Option[Int] = None) = {
    val req = HttpRequest(GET, url, body, params, headers)
    requestEffect(req, extractor, expectedStatus)
  }

  def head[A: Input](url: String, body: Option[A] = None, params: Seq[(String, String)], headers: Seq[(String, String)],
    extractor: ResponseExtractor = NoOpExtraction, expectedStatus: Option[Int] = None) = {
    val req = HttpRequest(HEAD, url, body, params, headers)
    requestEffect(req, extractor, expectedStatus)
  }

  def options[A: Input](url: String, body: Option[A] = None, params: Seq[(String, String)], headers: Seq[(String, String)],
    extractor: ResponseExtractor = NoOpExtraction, expectedStatus: Option[Int] = None) = {
    val req = HttpRequest(OPTIONS, url, body, params, headers)
    requestEffect(req, extractor, expectedStatus)
  }

  def delete[A: Input](url: String, body: Option[A] = None, params: Seq[(String, String)], headers: Seq[(String, String)],
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
      s"${h._1}$HeadersKeyValueDelim${h._2}"
    }.mkString(InterHeadersValueDelim)

  def decodeSessionHeaders(headers: String): Seq[(String, String)] =
    headers.split(InterHeadersValueDelim).toSeq.map { header ⇒
      val elms = header.split(HeadersKeyValueDelim)
      (elms.head, elms.tail.head)
    }
}