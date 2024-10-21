package com.github.agourlay.cornichon.http

import cats.Show
import cats.data.EitherT
import cats.effect.IO
import cats.effect.unsafe.IORuntime
import org.http4s.circe._
import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.http.client.HttpClient
import com.github.agourlay.cornichon.json.JsonPath
import com.github.agourlay.cornichon.json.CornichonJson._
import com.github.agourlay.cornichon.http.HttpStreams._
import com.github.agourlay.cornichon.resolver.Resolvable
import com.github.agourlay.cornichon.http.HttpService._
import com.github.agourlay.cornichon.util.TraverseUtils.traverseIL
import io.circe.{ Encoder, Json }
import org.http4s.EntityEncoder
import scala.concurrent.Future
import scala.concurrent.duration._

class HttpService(
    baseUrl: String,
    requestTimeout: FiniteDuration,
    client: HttpClient,
    config: Config)(implicit ioRuntime: IORuntime) {

  private def resolveAndParseBody[A: Show: Resolvable: Encoder](body: Option[A], scenarioContext: ScenarioContext): Either[CornichonError, Option[Json]] =
    body.map(scenarioContext.fillPlaceholders(_)) match {
      case None                      => rightNone
      case Some(Left(e))             => Left(e)
      case Some(Right(resolvedBody)) => parseDslJson(resolvedBody).map(Some.apply)
    }

  private def urlWithoutParams(url: String): String = {
    val index = url.indexOf('?')
    if (index == -1) url else url.substring(0, index)
  }

  private[http] def resolveRequestParts[A: Show: Resolvable: Encoder](
    url: String,
    body: Option[A],
    params: Seq[(String, String)],
    headers: Seq[(String, String)],
    ignoreFromWithHeaders: HeaderSelection)(scenarioContext: ScenarioContext): Either[CornichonError, (String, Option[Json], Seq[(String, String)], List[(String, String)])] =
    for {
      jsonBodyResolved <- resolveAndParseBody(body, scenarioContext)
      urlResolved <- scenarioContext.fillPlaceholders(url)
      completeUrlResolved = withBaseUrl(urlResolved)
      urlParams <- client.paramsFromUrl(completeUrlResolved)
      completeUrlResolvedNoParams = urlWithoutParams(completeUrlResolved)
      explicitParams <- scenarioContext.fillPlaceholdersPairs(params)
      allParams = urlParams ++ explicitParams
      extractedWithHeaders <- extractWithHeadersSession(scenarioContext.session)
      allHeaders = headers ++ ignoreHeadersSelection(extractedWithHeaders, ignoreFromWithHeaders)
      headersResolved <- scenarioContext.fillPlaceholdersPairs(allHeaders)
    } yield (completeUrlResolvedNoParams, jsonBodyResolved, allParams, headersResolved)

  private def runRequest[A: Show: Resolvable: Encoder](
    r: HttpRequest[A],
    expectedStatus: Option[Int],
    extractor: ResponseExtractor,
    ignoreFromWithHeaders: HeaderSelection)(scenarioContext: ScenarioContext): EitherT[IO, CornichonError, Session] =
    for {
      (url, jsonBody, params, headers) <- EitherT.fromEither[IO](resolveRequestParts(r.url, r.body, r.params, r.headers, ignoreFromWithHeaders)(scenarioContext))
      resolvedRequest = HttpRequest(r.method, url, jsonBody, params, headers)
      configuredRequest = configureRequest(resolvedRequest, config)
      resp <- client.runRequest(configuredRequest, requestTimeout)(circeJsonEncoder, Json.showJson) // TODO remove implicits when removing Scala 2.12
      newSession <- EitherT.fromEither[IO](handleResponse(resp, configuredRequest.detailedDescription, expectedStatus, extractor)(scenarioContext.session))
    } yield newSession

  private def runStreamRequest(r: HttpStreamedRequest, expectedStatus: Option[Int], extractor: ResponseExtractor)(scenarioContext: ScenarioContext) =
    for {
      (url, _, params, headers) <- EitherT.fromEither[IO](resolveRequestParts[String](r.url, None, r.params, r.headers, SelectNone)(scenarioContext))
      resolvedRequest = HttpStreamedRequest(r.stream, url, r.takeWithin, params, headers)
      resp <- EitherT(client.openStream(resolvedRequest, requestTimeout))
      newSession <- EitherT.fromEither[IO](handleResponse(resp, resolvedRequest.detailedDescription, expectedStatus, extractor)(scenarioContext.session))
    } yield newSession

  private def withBaseUrl(input: String) = {
    val trimmedUrl = input.trim
    if (baseUrl.isEmpty) trimmedUrl
    // the base URL is not applied if the input URL already starts with the protocol
    else if (trimmedUrl.startsWith("https://") || trimmedUrl.startsWith("http://")) trimmedUrl
    else baseUrl + trimmedUrl
  }

  def requestEffectT[A: Show: Resolvable: Encoder](
    request: HttpRequest[A],
    extractor: ResponseExtractor = NoOpExtraction,
    expectedStatus: Option[Int] = None,
    ignoreFromWithHeaders: HeaderSelection = SelectNone): ScenarioContext => EitherT[Future, CornichonError, Session] =
    sc => {
      val f = requestEffect(request, extractor, expectedStatus, ignoreFromWithHeaders)
      EitherT(f(sc))
    }

  def requestEffectIO[A: Show: Resolvable: Encoder](
    request: HttpRequest[A],
    extractor: ResponseExtractor = NoOpExtraction,
    expectedStatus: Option[Int] = None,
    ignoreFromWithHeaders: HeaderSelection = SelectNone): ScenarioContext => IO[Either[CornichonError, Session]] =
    sc => runRequest(request, expectedStatus, extractor, ignoreFromWithHeaders)(sc).value

  def requestEffect[A: Show: Resolvable: Encoder](
    request: HttpRequest[A],
    extractor: ResponseExtractor = NoOpExtraction,
    expectedStatus: Option[Int] = None,
    ignoreFromWithHeaders: HeaderSelection = SelectNone): ScenarioContext => Future[Either[CornichonError, Session]] =
    sc => {
      val effect = requestEffectIO(request, extractor, expectedStatus, ignoreFromWithHeaders)
      effect(sc).unsafeToFuture()
    }

  def streamEffect(request: HttpStreamedRequest, expectedStatus: Option[Int] = None, extractor: ResponseExtractor = NoOpExtraction): ScenarioContext => Future[Either[CornichonError, Session]] =
    rs => runStreamRequest(request, expectedStatus, extractor)(rs).value.unsafeToFuture()

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
  private val rightNone = Right(None)
  // cache json encoder to avoid recreating it for each request
  implicit lazy val circeJsonEncoder: EntityEncoder[IO, Json] = jsonEncoder[IO]
  object SessionKeys {
    val lastResponseBodyKey = "last-response-body"
    val lastResponseStatusKey = "last-response-status"
    val lastResponseHeadersKey = "last-response-headers"
    val lastResponseRequestKey = "last-response-request"
    val withHeadersKey = "with-headers"
    // Using non-ASCII chars to assure that those won't be present inside the headers.
    val headersKeyValueDelim = '→'
    val interHeadersValueDelim = '¦'
  }

  import HttpService.SessionKeys._

  def extractWithHeadersSession(session: Session): Either[CornichonError, Seq[(String, String)]] =
    session.getOpt(withHeadersKey) match {
      case Some(h) => decodeSessionHeaders(h)
      case None    => rightNil
    }

  def encodeSessionHeader(name: String, value: String) =
    s"$name$headersKeyValueDelim$value"

  def encodeSessionHeaders(headers: Vector[(String, String)]): String = {
    val len = headers.length
    val builder = new StringBuilder(len * 10)
    var i = 0
    while (i < len) {
      val (name, value) = headers(i)
      // unroll `encodeSessionHeader` to avoid creating intermediate strings
      builder.append(name)
      builder.append(headersKeyValueDelim)
      builder.append(value)
      if (i < len - 1)
        builder.append(interHeadersValueDelim)
      i += 1
    }
    builder.toString
  }

  def decodeSessionHeaders(headers: String): Either[CornichonError, List[(String, String)]] =
    traverseIL(headers.split(interHeadersValueDelim).iterator) { header =>
      val index = header.indexOf(headersKeyValueDelim.toInt)
      if (index == -1)
        Left(BadSessionHeadersEncoding(header))
      else
        Right(header.substring(0, index) -> header.substring(index + 1))
    }

  private def configureRequest[A](req: HttpRequest[A], config: Config): HttpRequest[A] = {
    if (config.traceRequests)
      println(DebugLogInstruction(req.detailedDescription, 1).colorized)
    if (config.warnOnDuplicateHeaders && req.headers.groupBy(_._1).exists(_._2.size > 1))
      println(WarningLogInstruction(s"\n**Warning**\nduplicate headers detected in request:\n${req.detailedDescription}", 1).colorized)
    if (config.failOnDuplicateHeaders && req.headers.groupBy(_._1).exists(_._2.size > 1))
      throw BasicError(s"duplicate headers detected in request:\n${req.detailedDescription}").toException
    else
      req
  }

  private def ignoreHeadersSelection(headers: Seq[(String, String)], ignore: HeaderSelection): Seq[(String, String)] =
    ignore match {
      case SelectNone     => headers
      case SelectAll      => Nil
      case ByNames(names) => headers.filterNot { case (n, _) => names.contains(n) }
    }

  def fillInSessionWithResponse(response: HttpResponse, session: Session, extractor: ResponseExtractor, requestDescription: String): Either[CornichonError, Session] = {
    val updatedSession = session
      .addValueInternal(lastResponseStatusKey, statusToString(response.status))
      .addValueInternal(lastResponseBodyKey, response.body)
      .addValueInternal(lastResponseHeadersKey, encodeSessionHeaders(response.headers))
      .addValueInternal(lastResponseRequestKey, requestDescription)

    extractor match {
      case NoOpExtraction =>
        Right(updatedSession)
      case RootExtractor(targetKey) =>
        updatedSession.addValue(targetKey, response.body)
      case PathExtractor(path, targetKey) =>
        JsonPath.runStrict(path, response.body)
          .flatMap(extractedJson => updatedSession.addValue(targetKey, jsonStringValue(extractedJson)))
    }
  }

  private def handleResponse(resp: HttpResponse, requestDescription: String, expectedStatus: Option[Int], extractor: ResponseExtractor)(session: Session): Either[CornichonError, Session] =
    expectedStatus match {
      case Some(expectedStatus) if resp.status != expectedStatus =>
        Left(StatusNonExpected(expectedStatus, resp.status, resp.headers, resp.body, requestDescription))
      case _ => fillInSessionWithResponse(resp, session, extractor, requestDescription)
    }

  // Avoid reallocating known strings
  private def statusToString(status: Int): String =
    status match {
      case 200   => "200"
      case 201   => "201"
      case 400   => "400"
      case 401   => "401"
      case 404   => "404"
      case 500   => "500"
      case 502   => "502"
      case 503   => "503"
      case other => Integer.toString(other)
    }
}