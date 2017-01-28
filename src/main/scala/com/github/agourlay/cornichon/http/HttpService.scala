package com.github.agourlay.cornichon.http

import java.util.concurrent.ScheduledExecutorService

import cats.Show
import cats.data.EitherT
import cats.syntax.either._
import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.http.client.HttpClient
import com.github.agourlay.cornichon.json.JsonPath
import com.github.agourlay.cornichon.json.CornichonJson._
import com.github.agourlay.cornichon.http.HttpStreams._
import com.github.agourlay.cornichon.resolver.{ Resolvable, Resolver }
import com.github.agourlay.cornichon.http.HttpService._
import com.github.agourlay.cornichon.http.HttpService.SessionKeys._
import com.github.agourlay.cornichon.util.Timeouts
import com.github.agourlay.cornichon.util.Instances._
import io.circe.Encoder

import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration._
import scala.util.control.NonFatal

class HttpService(baseUrl: String, requestTimeout: FiniteDuration, client: HttpClient, resolver: Resolver)(implicit ec: ExecutionContext, timer: ScheduledExecutorService) {

  private def resolveRequest[A: Show: Resolvable: Encoder](r: HttpRequest[A])(s: Session) =
    for {
      resolvedRequestParts ← resolveRequestParts(r.url, r.body, r.params, r.headers, r.formData)(s)
      (url, jsonBody, params, headers, formData) = resolvedRequestParts
    } yield HttpRequest(r.method, url, jsonBody, params, headers, formData)

  private def resolveStreamedRequest[A: Show: Resolvable: Encoder](r: HttpStreamedRequest)(s: Session) =
    for {
      resolvedRequestParts ← resolveRequestParts(r.url, None, r.params, r.headers, r.formData)(s)
      (url, _, params, headers, formData) = resolvedRequestParts
    } yield HttpStreamedRequest(r.stream, url, r.takeWithin, params, headers, formData)

  private def resolveRequestParts[A: Show: Resolvable: Encoder](url: String, body: Option[A], params: Seq[(String, String)], headers: Seq[(String, String)], formData: Seq[(String, String)])(s: Session) = {
    for {
      bodyResolved ← body.map(resolver.fillPlaceholders(_)(s).map(Some(_))).getOrElse(Right(None))
      jsonBodyResolved ← bodyResolved.map(parseJson(_).map(Some(_))).getOrElse(Right(None))
      urlResolved ← resolver.fillPlaceholders(url)(s)
      completeUrlResolved ← resolver.fillPlaceholders(withBaseUrl(urlResolved))(s)
      paramsResolved ← resolveParams(url, params)(s)
      headersResolved ← resolver.fillPlaceholders(headers ++ extractWithHeadersSession(s))(s)
      formDataResolved ← resolver.fillPlaceholders(formData)(s)
    } yield (completeUrlResolved, jsonBodyResolved, paramsResolved, headersResolved, formDataResolved)
  }

  private def runRequest[A: Show: Resolvable: Encoder](r: HttpRequest[A], expectedStatus: Option[Int], extractor: ResponseExtractor)(s: Session) =
    for {
      resolvedRequest ← EitherT(Future.successful(resolveRequest(r)(s)))
      resp ← handleRequestFuture(resolvedRequest, requestTimeout) {
        client.runRequest(resolvedRequest.method, resolvedRequest.url, resolvedRequest.body, resolvedRequest.params, resolvedRequest.headers, resolvedRequest.formData)
      }
      newSession ← EitherT(Future.successful(handleResponse(resp, expectedStatus, extractor)(s)))
    } yield (resp, newSession)

  def runStreamRequest(r: HttpStreamedRequest, expectedStatus: Option[Int], extractor: ResponseExtractor)(s: Session) =
    for {
      resolvedRequest ← EitherT(Future.successful(resolveStreamedRequest[String](r)(s)))
      resp ← handleRequestFuture(resolvedRequest, requestTimeout) {
        client.openStream(r.stream, resolvedRequest.url, resolvedRequest.params, resolvedRequest.headers, resolvedRequest.formData, r.takeWithin)
      }
      newSession ← EitherT(Future.successful(handleResponse(resp, expectedStatus, extractor)(s)))
    } yield (resp, newSession)

  def expectStatusCode(httpResponse: CornichonHttpResponse, expected: Option[Int]): Either[CornichonError, CornichonHttpResponse] =
    expected.map { expectedStatus ⇒
      if (httpResponse.status == expectedStatus)
        Right(httpResponse)
      else
        Left(StatusNonExpected(expectedStatus, httpResponse))
    }.getOrElse(Right(httpResponse))

  def resolveParams(url: String, params: Seq[(String, String)])(session: Session): Either[CornichonError, Seq[(String, String)]] = {
    val urlsParamsPart = url.dropWhile(_ != '?').drop(1)
    val urlParams = if (urlsParamsPart.trim.isEmpty) Seq.empty else client.paramsFromUrl(urlsParamsPart)
    resolver.fillPlaceholders(urlParams ++ params)(session)
  }

  private def handleResponse(resp: CornichonHttpResponse, expectedStatus: Option[Int], extractor: ResponseExtractor)(session: Session) =
    for {
      resExpected ← expectStatusCode(resp, expectedStatus)
      newSession ← fillInSessionWithResponse(session, resExpected, extractor)
    } yield newSession

  private def commonSessionExtraction(session: Session, response: CornichonHttpResponse): Session =
    session.addValues(Seq(
      lastResponseStatusKey → response.status.intValue().toString,
      lastResponseBodyKey → response.body,
      lastResponseHeadersKey → encodeSessionHeaders(response)
    ))

  def fillInSessionWithResponse(session: Session, response: CornichonHttpResponse, extractor: ResponseExtractor): Either[CornichonError, Session] =
    extractor match {
      case NoOpExtraction ⇒
        Right(commonSessionExtraction(session, response))

      case RootExtractor(targetKey) ⇒
        Right(commonSessionExtraction(session, response).addValue(targetKey, response.body))

      case PathExtractor(path, targetKey) ⇒
        JsonPath.run(path, response.body)
          .map { extractedJson ⇒
            commonSessionExtraction(session, response).addValue(targetKey, jsonStringValue(extractedJson))
          }
    }

  private def extractWithHeadersSession(session: Session): Seq[(String, String)] =
    session.getOpt(withHeadersKey).fold(Seq.empty[(String, String)]) { headers ⇒
      decodeSessionHeaders(headers)
    }

  private def withBaseUrl(input: String) =
    if (baseUrl.isEmpty) input
    // the base URL is not applied if the input URL already starts with the protocol
    else if (input.startsWith("https://") || input.startsWith("http://")) input
    else baseUrl + input

  private def handleRequestFuture[A: Show](request: A, t: FiniteDuration)(f: Future[Either[CornichonError, CornichonHttpResponse]]): EitherT[Future, CornichonError, CornichonHttpResponse] = {
    val failedAfter = Timeouts.failAfter(t)(f)(TimeoutErrorAfter(request, t))
      .recover {
        case NonFatal(failure) ⇒ failure match {
          case t @ TimeoutErrorAfter(_, _) ⇒ Left(t)
          case t: Throwable                ⇒ Left(RequestError(request, t))
        }
      }
    EitherT(failedAfter)
  }

  def requestEffect[A: Show: Resolvable: Encoder](request: HttpRequest[A], extractor: ResponseExtractor = NoOpExtraction, expectedStatus: Option[Int] = None): Session ⇒ Future[Session] =
    s ⇒ runRequest(request, expectedStatus, extractor)(s).fold(e ⇒ throw e, _._2)

  def streamEffect(request: HttpStreamedRequest, expectedStatus: Option[Int] = None, extractor: ResponseExtractor = NoOpExtraction): Session ⇒ Future[Session] =
    s ⇒ runStreamRequest(request, expectedStatus, extractor)(s).fold(e ⇒ throw e, _._2)

  def openSSE(url: String, takeWithin: FiniteDuration, params: Seq[(String, String)], headers: Seq[(String, String)], formData: Seq[(String, String)],
    extractor: ResponseExtractor = NoOpExtraction, expectedStatus: Option[Int] = None) = {
    val req = HttpStreamedRequest(SSE, url, takeWithin, params, headers, formData)
    streamEffect(req, expectedStatus, extractor)
  }

  def openWS(url: String, takeWithin: FiniteDuration, params: Seq[(String, String)], headers: Seq[(String, String)], formData: Seq[(String, String)],
    extractor: ResponseExtractor = NoOpExtraction, expectedStatus: Option[Int] = None) = {
    val req = HttpStreamedRequest(WS, url, takeWithin, params, headers, formData)
    streamEffect(req, expectedStatus, extractor)
  }
}

sealed trait ResponseExtractor
case class RootExtractor(targetKey: String) extends ResponseExtractor
case class PathExtractor(path: String, targetKey: String) extends ResponseExtractor
object NoOpExtraction extends ResponseExtractor

object HttpService {
  object SessionKeys {
    val lastResponseBodyKey = "last-response-body"
    val lastResponseStatusKey = "last-response-status"
    val lastResponseHeadersKey = "last-response-headers"
    val withHeadersKey = "with-headers"
    // Using non-ASCII chars to assure that those won't be present inside the headers.
    val headersKeyValueDelim = '→'
    val interHeadersValueDelim = '¦'
  }

  import HttpService.SessionKeys._

  def encodeSessionHeader(name: String, value: String) =
    s"$name$headersKeyValueDelim$value"

  def encodeSessionHeaders(response: CornichonHttpResponse): String =
    response.headers.map {
      case (name, value) ⇒ encodeSessionHeader(name, value)
    }.mkString(interHeadersValueDelim.toString)

  def decodeSessionHeaders(headers: String): Seq[(String, String)] =
    headers.split(interHeadersValueDelim).toSeq.map { header ⇒
      val elms = header.split(headersKeyValueDelim)
      (elms.head, elms.tail.head)
    }
}