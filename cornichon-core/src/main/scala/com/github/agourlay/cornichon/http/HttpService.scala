package com.github.agourlay.cornichon.http

import cats.Show
import cats.data.EitherT
import cats.syntax.traverse._
import cats.syntax.show._
import cats.syntax.either._
import cats.instances.int._
import cats.instances.list._
import cats.instances.either._
import cats.instances.string._
import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.http.client.HttpClient
import com.github.agourlay.cornichon.json.JsonPath
import com.github.agourlay.cornichon.json.CornichonJson._
import com.github.agourlay.cornichon.http.HttpStreams._
import com.github.agourlay.cornichon.resolver.{ PlaceholderResolver, Resolvable }
import com.github.agourlay.cornichon.http.HttpService._
import com.github.agourlay.cornichon.http.HttpRequest._
import com.github.agourlay.cornichon.util.Caching
import io.circe.Encoder
import monix.eval.Task
import monix.eval.Task._
import monix.execution.Scheduler

import scala.concurrent.Future
import scala.concurrent.duration._

class HttpService(
    baseUrl: String,
    requestTimeout: FiniteDuration,
    client: HttpClient,
    resolver: PlaceholderResolver,
    config: Config)(implicit ec: Scheduler) {

  private val fullUrlCache = Caching.buildCache[String, String]()

  private def resolveRequestParts[A: Show: Resolvable: Encoder](
    url: String,
    body: Option[A],
    params: Seq[(String, String)],
    headers: Seq[(String, String)])(ignoreFromWithHeaders: HeaderSelection)(s: Session) =
    for {
      bodyResolved ← body.map(resolver.fillPlaceholders(_)(s).map(Some.apply)).getOrElse(rightNone)
      jsonBodyResolved ← bodyResolved.map(parseJson(_).map(Some.apply)).getOrElse(rightNone)
      urlResolved ← resolver.fillPlaceholders(url)(s)
      completeUrlResolved ← resolver.fillPlaceholders(withBaseUrl(urlResolved))(s)
      urlParams ← client.paramsFromUrl(completeUrlResolved)
      explicitParams ← resolver.fillPlaceholders(params)(s)
      allParams = urlParams ++ explicitParams
      extractedWithHeaders ← extractWithHeadersSession(s)
      allHeaders = headers ++ ignoreHeadersSelection(extractedWithHeaders, ignoreFromWithHeaders)
      headersResolved ← resolver.fillPlaceholders(allHeaders)(s)
    } yield (completeUrlResolved, jsonBodyResolved, allParams, headersResolved)

  private def runRequest[A: Show: Resolvable: Encoder](
    r: HttpRequest[A],
    expectedStatus: Option[Int],
    extractor: ResponseExtractor,
    ignoreFromWithHeaders: HeaderSelection)(s: Session): EitherT[Task, CornichonError, Session] =
    for {
      (url, jsonBody, params, headers) ← EitherT.fromEither[Task](resolveRequestParts(r.url, r.body, r.params, r.headers)(ignoreFromWithHeaders)(s))
      resolvedRequest = HttpRequest(r.method, url, jsonBody, params, headers)
      configuredRequest = configureRequest(resolvedRequest, config)
      resp ← client.runRequest(configuredRequest, requestTimeout)
      newSession ← EitherT.fromEither[Task](handleResponse(resp, expectedStatus, extractor)(s))
    } yield newSession

  private def runStreamRequest(r: HttpStreamedRequest, expectedStatus: Option[Int], extractor: ResponseExtractor)(s: Session) =
    for {
      (url, _, params, headers) ← EitherT.fromEither[Task](resolveRequestParts(r.url, None, r.params, r.headers)(SelectNone)(s))
      resolvedRequest = HttpStreamedRequest(r.stream, url, r.takeWithin, params, headers)
      resp ← EitherT(client.openStream(resolvedRequest, requestTimeout))
      newSession ← EitherT.fromEither[Task](handleResponse(resp, expectedStatus, extractor)(s))
    } yield newSession

  private def withBaseUrl(input: String) = {
    def urlBuilder(url: String) = {
      val trimmedUrl = url.trim
      if (baseUrl.isEmpty) trimmedUrl
      // the base URL is not applied if the input URL already starts with the protocol
      else if (trimmedUrl.startsWith("https://") || trimmedUrl.startsWith("http://")) trimmedUrl
      else baseUrl + trimmedUrl
    }

    fullUrlCache.get(input, k ⇒ urlBuilder(k))
  }

  def requestEffectT[A: Show: Resolvable: Encoder](
    request: HttpRequest[A],
    extractor: ResponseExtractor = NoOpExtraction,
    expectedStatus: Option[Int] = None,
    ignoreFromWithHeaders: HeaderSelection = SelectNone): Session ⇒ EitherT[Future, CornichonError, Session] =
    s ⇒ {
      val f = requestEffect(request, extractor, expectedStatus, ignoreFromWithHeaders)
      EitherT(f(s))
    }

  // Just used for internal optimisation for the time being
  private[cornichon] def requestEffectTask[A: Show: Resolvable: Encoder](
    request: HttpRequest[A],
    extractor: ResponseExtractor = NoOpExtraction,
    expectedStatus: Option[Int] = None,
    ignoreFromWithHeaders: HeaderSelection = SelectNone): Session ⇒ Task[Either[CornichonError, Session]] =
    s ⇒ runRequest(request, expectedStatus, extractor, ignoreFromWithHeaders)(s).value

  def requestEffect[A: Show: Resolvable: Encoder](
    request: HttpRequest[A],
    extractor: ResponseExtractor = NoOpExtraction,
    expectedStatus: Option[Int] = None,
    ignoreFromWithHeaders: HeaderSelection = SelectNone): Session ⇒ Future[Either[CornichonError, Session]] =
    s ⇒ {
      val effect = requestEffectTask(request, extractor, expectedStatus, ignoreFromWithHeaders)
      effect(s).runToFuture
    }

  def streamEffect(request: HttpStreamedRequest, expectedStatus: Option[Int] = None, extractor: ResponseExtractor = NoOpExtraction): Session ⇒ Future[Either[CornichonError, Session]] =
    s ⇒ runStreamRequest(request, expectedStatus, extractor)(s).value.runToFuture

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

sealed trait HeaderSelection
object SelectAll extends HeaderSelection
object SelectNone extends HeaderSelection
case class ByNames(names: Seq[String]) extends HeaderSelection

object HttpService {
  val rightNil = Right(Nil)
  val rightNone = Right(None)
  object SessionKeys {
    val lastResponseBodyKey = "last-response-body"
    val lastResponseStatusKey = "last-response-status"
    val lastResponseHeadersKey = "last-response-headers"
    val withHeadersKey = "with-headers"
    // Using non-ASCII chars to assure that those won't be present inside the headers.
    val headersKeyValueDelim = '→'
    val interHeadersValueDelim = '¦'
    val interHeadersValueDelimString = interHeadersValueDelim.toString
  }

  import HttpService.SessionKeys._

  def extractWithHeadersSession(session: Session): Either[CornichonError, Seq[(String, String)]] =
    session.getOpt(withHeadersKey) match {
      case Some(h) ⇒ decodeSessionHeaders(h)
      case None    ⇒ rightNil
    }

  def encodeSessionHeader(name: String, value: String) =
    s"$name$headersKeyValueDelim$value"

  def encodeSessionHeaders(headers: Seq[(String, String)]): String =
    headers.map {
      case (name, value) ⇒ encodeSessionHeader(name, value)
    }.mkString(interHeadersValueDelimString)

  def decodeSessionHeaders(headers: String): Either[CornichonError, List[(String, String)]] =
    headers.split(interHeadersValueDelim).toList.traverse { header ⇒
      val elms = header.split(headersKeyValueDelim)
      if (elms.length != 2)
        BadSessionHeadersEncoding(header).asLeft
      else
        (elms(0) -> elms(1)).asRight
    }

  def configureRequest[A: Show](req: HttpRequest[A], config: Config): HttpRequest[A] = {
    if (config.traceRequests)
      println(DebugLogInstruction(req.show, 1).colorized)
    if (config.warnOnDuplicateHeaders && req.headers.groupBy(_._1).exists(_._2.size > 1))
      println(WarningLogInstruction(s"\n**Warning**\nduplicate headers detected in request:\n${req.show}", 1).colorized)
    if (config.failOnDuplicateHeaders && req.headers.groupBy(_._1).exists(_._2.size > 1))
      throw BasicError(s"duplicate headers detected in request:\n${req.show}").toException
    if (config.addAcceptGzipByDefault)
      req.addHeaders("Accept-Encoding" -> "gzip")
    else
      req
  }

  def ignoreHeadersSelection(headers: Seq[(String, String)], ignore: HeaderSelection): Seq[(String, String)] =
    ignore match {
      case SelectNone     ⇒ headers
      case SelectAll      ⇒ Nil
      case ByNames(names) ⇒ headers.filterNot { case (n, _) ⇒ names.contains(n) }
    }

  def expectStatusCode(httpResponse: CornichonHttpResponse, expected: Option[Int]): Either[CornichonError, CornichonHttpResponse] =
    expected match {
      case None ⇒
        httpResponse.asRight
      case Some(expectedStatus) if httpResponse.status == expectedStatus ⇒
        httpResponse.asRight
      case Some(expectedStatus) ⇒
        StatusNonExpected(expectedStatus, httpResponse.status, httpResponse.headers, httpResponse.body).asLeft
    }

  def fillInSessionWithResponse(session: Session, extractor: ResponseExtractor)(response: CornichonHttpResponse): Either[CornichonError, Session] =
    commonSessionExtraction(session, response).flatMap { filledSession ⇒
      extractor match {
        case NoOpExtraction ⇒
          filledSession.asRight
        case RootExtractor(targetKey) ⇒
          filledSession.addValue(targetKey, response.body)
        case PathExtractor(path, targetKey) ⇒
          JsonPath.runStrict(path, response.body)
            .flatMap(extractedJson ⇒ filledSession.addValue(targetKey, jsonStringValue(extractedJson)))
      }
    }

  def handleResponse(resp: CornichonHttpResponse, expectedStatus: Option[Int], extractor: ResponseExtractor)(session: Session): Either[CornichonError, Session] =
    expectStatusCode(resp, expectedStatus)
      .flatMap(fillInSessionWithResponse(session, extractor))

  def commonSessionExtraction(session: Session, response: CornichonHttpResponse): Either[CornichonError, Session] =
    session.addValues(
      lastResponseStatusKey → response.status.toString,
      lastResponseBodyKey → response.body,
      lastResponseHeadersKey → encodeSessionHeaders(response.headers)
    )
}