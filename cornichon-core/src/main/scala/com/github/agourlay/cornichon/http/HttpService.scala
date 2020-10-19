package com.github.agourlay.cornichon.http

import cats.Show
import cats.data.EitherT
import cats.syntax.traverse._
import cats.syntax.show._
import cats.syntax.either._
import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.http.client.HttpClient
import com.github.agourlay.cornichon.json.JsonPath
import com.github.agourlay.cornichon.json.CornichonJson._
import com.github.agourlay.cornichon.http.HttpStreams._
import com.github.agourlay.cornichon.resolver.Resolvable
import com.github.agourlay.cornichon.http.HttpService._
import com.github.agourlay.cornichon.util.Caching
import com.github.agourlay.cornichon.util.Printing.printArrowPairs
import monix.eval.Task
import monix.eval.Task._
import monix.execution.Scheduler
import org.http4s.Request

import scala.concurrent.Future
import scala.concurrent.duration._

class HttpService(
    baseUrl: String,
    requestTimeout: FiniteDuration,
    client: HttpClient,
    config: Config)(implicit ec: Scheduler) {

  // Cannot be globally shared because it depends on `baseUrl`
  private val fullUrlCache = Caching.buildCache[String, String]()

  private def resolveAndEncodeBody[DSL_INPUT: Resolvable, ENTITY_HTTP](body: Option[DSL_INPUT], scenarioContext: ScenarioContext)(implicit hp: HttpPayload[DSL_INPUT, ENTITY_HTTP]): Either[CornichonError, Option[ENTITY_HTTP]] =
    body.map(scenarioContext.fillPlaceholders(_)) match {
      case None                      => rightNone
      case Some(Left(e))             => Left(e)
      case Some(Right(resolvedBody)) => hp.toEntity(resolvedBody).map(Some.apply)
    }

  private def resolveRequestParts[DSL_INPUT: Resolvable, ENTITY_HTTP](
    url: String,
    body: Option[DSL_INPUT],
    params: Seq[(String, String)],
    headers: Seq[(String, String)],
    ignoreFromWithHeaders: HeaderSelection)(scenarioContext: ScenarioContext)(implicit hp: HttpPayload[DSL_INPUT, ENTITY_HTTP]): Either[CornichonError, (String, Option[ENTITY_HTTP], Seq[(String, String)], List[(String, String)])] =
    for {
      entityBodyResolved <- resolveAndEncodeBody(body, scenarioContext)
      urlResolved <- scenarioContext.fillPlaceholders(url)
      completeUrlResolved = withBaseUrl(urlResolved)
      urlParams <- client.paramsFromUrl(completeUrlResolved)
      explicitParams <- scenarioContext.fillPlaceholders(params)
      allParams = urlParams ++ explicitParams
      extractedWithHeaders <- extractWithHeadersSession(scenarioContext.session)
      allHeaders = headers ++ ignoreHeadersSelection(extractedWithHeaders, ignoreFromWithHeaders)
      headersResolved <- scenarioContext.fillPlaceholders(allHeaders)
    } yield (completeUrlResolved, entityBodyResolved, allParams, headersResolved)

  private def runRequest[DSL_INPUT: Show: Resolvable, ENTITY_HTTP: Show](
    r: DslHttpRequest[DSL_INPUT, ENTITY_HTTP],
    expectedStatus: Option[Int],
    extractor: ResponseExtractor,
    ignoreFromWithHeaders: HeaderSelection)(scenarioContext: ScenarioContext)(implicit hp: HttpPayload[DSL_INPUT, ENTITY_HTTP]): EitherT[Task, CornichonError, Session] =
    for {
      (url, entityBody, params, headers) <- EitherT.fromEither[Task](resolveRequestParts(r.url, r.body, r.params, r.headers, ignoreFromWithHeaders)(scenarioContext))
      resolvedRequestToSubmit = HttpRequest(r.method, url, entityBody, params, headers)
      _ <- EitherT.fromEither[Task](requestConfigurationHandler(resolvedRequestToSubmit, config))
      // reqSent contains the headers added by the EntityEncoder
      (reqSent, response) <- client.runRequest(resolvedRequestToSubmit, requestTimeout)(hp.entityEncoder)
      prettyHttp4sReq = prettyHttp4sRequest(reqSent, entityBody)
      newSession <- EitherT.fromEither[Task](handleResponse(response, prettyHttp4sReq, expectedStatus, extractor)(scenarioContext.session))
    } yield newSession

  private def prettyHttp4sRequest[A: Show](r: Request[Task], entityOpt: Option[A]) = {
    val body = entityOpt.fold("without body")(b => s"with body\n${b.show}")
    val params = if (r.params.isEmpty) "without parameters" else s"with parameters ${printArrowPairs(r.params.toSeq)}"
    val headers = if (r.headers.isEmpty) "without headers" else s"with headers ${printArrowPairs(r.headers.iterator.map(h => (h.name.value, h.value)).toSeq)}"

    s"""|HTTP ${r.method.name} request to ${r.uri.toString()}
        |$params
        |$headers
        |$body""".stripMargin
  }

  private def runStreamRequest(r: DslHttpStreamedRequest, expectedStatus: Option[Int], extractor: ResponseExtractor)(scenarioContext: ScenarioContext) = {
    import io.circe.Json
    import cats.instances.string._
    implicit val hp = HttpPayload.fromCirceEncoderHttpPayload[String]
    for {
      (url, _, params, headers) <- EitherT.fromEither[Task](resolveRequestParts[String, Json](r.url, None, r.params, r.headers, SelectNone)(scenarioContext))
      resolvedRequest = DslHttpStreamedRequest(r.stream, url, r.takeWithin, params, headers)
      resp <- EitherT(client.openStream(resolvedRequest, requestTimeout))
      newSession <- EitherT.fromEither[Task](handleResponse(resp, resolvedRequest.show, expectedStatus, extractor)(scenarioContext.session))
    } yield newSession
  }

  private def withBaseUrl(input: String) = {
    def urlBuilder(url: String) = {
      val trimmedUrl = url.trim
      if (baseUrl.isEmpty) trimmedUrl
      // the base URL is not applied if the input URL already starts with the protocol
      else if (trimmedUrl.startsWith("https://") || trimmedUrl.startsWith("http://")) trimmedUrl
      else baseUrl + trimmedUrl
    }

    fullUrlCache.get(input, k => urlBuilder(k))
  }

  def requestEffectT[DSL_INPUT: Show: Resolvable, ENTITY_HTTP: Show](
    request: DslHttpRequest[DSL_INPUT, ENTITY_HTTP],
    extractor: ResponseExtractor = NoOpExtraction,
    expectedStatus: Option[Int] = None,
    ignoreFromWithHeaders: HeaderSelection = SelectNone): ScenarioContext => EitherT[Future, CornichonError, Session] =
    sc => {
      val f = requestEffect(request, extractor, expectedStatus, ignoreFromWithHeaders)
      EitherT(f(sc))
    }

  // Just used for internal optimisation for the time being
  private[cornichon] def requestEffectTask[DSL_INPUT: Show: Resolvable, ENTITY_HTTP: Show](
    request: DslHttpRequest[DSL_INPUT, ENTITY_HTTP],
    extractor: ResponseExtractor = NoOpExtraction,
    expectedStatus: Option[Int] = None,
    ignoreFromWithHeaders: HeaderSelection = SelectNone): ScenarioContext => Task[Either[CornichonError, Session]] =
    sc => {
      implicit val hp = request.hp
      runRequest(request, expectedStatus, extractor, ignoreFromWithHeaders)(sc).value
    }

  def requestEffect[DSL_INPUT: Show: Resolvable, ENTITY_HTTP: Show](
    request: DslHttpRequest[DSL_INPUT, ENTITY_HTTP],
    extractor: ResponseExtractor = NoOpExtraction,
    expectedStatus: Option[Int] = None,
    ignoreFromWithHeaders: HeaderSelection = SelectNone): ScenarioContext => Future[Either[CornichonError, Session]] =
    sc => {
      val effect = requestEffectTask(request, extractor, expectedStatus, ignoreFromWithHeaders)
      effect(sc).runToFuture
    }

  def streamEffect(request: DslHttpStreamedRequest, expectedStatus: Option[Int] = None, extractor: ResponseExtractor = NoOpExtraction): ScenarioContext => Future[Either[CornichonError, Session]] =
    rs => runStreamRequest(request, expectedStatus, extractor)(rs).value.runToFuture

  def openSSE(url: String, takeWithin: FiniteDuration, params: Seq[(String, String)], headers: Seq[(String, String)],
    extractor: ResponseExtractor = NoOpExtraction, expectedStatus: Option[Int] = None) = {
    val req = DslHttpStreamedRequest(SSE, url, takeWithin, params, headers)
    streamEffect(req, expectedStatus, extractor)
  }

  def openWS(url: String, takeWithin: FiniteDuration, params: Seq[(String, String)], headers: Seq[(String, String)],
    extractor: ResponseExtractor = NoOpExtraction, expectedStatus: Option[Int] = None) = {
    val req = DslHttpStreamedRequest(WS, url, takeWithin, params, headers)
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
    val lastResponseRequestKey = "last-response-request"
    val withHeadersKey = "with-headers"
    // Using non-ASCII chars to assure that those won't be present inside the headers.
    val headersKeyValueDelim = '→'
    val interHeadersValueDelim = '¦'
    val interHeadersValueDelimString = interHeadersValueDelim.toString
  }

  import HttpService.SessionKeys._

  def extractWithHeadersSession(session: Session): Either[CornichonError, Seq[(String, String)]] =
    session.getOpt(withHeadersKey) match {
      case Some(h) => decodeSessionHeaders(h)
      case None    => rightNil
    }

  def encodeSessionHeader(name: String, value: String) =
    s"$name$headersKeyValueDelim$value"

  def encodeSessionHeaders(headers: Seq[(String, String)]): String =
    headers.iterator
      .map { case (name, value) => encodeSessionHeader(name, value) }
      .mkString(interHeadersValueDelimString)

  def decodeSessionHeaders(headers: String): Either[CornichonError, List[(String, String)]] =
    headers.split(interHeadersValueDelim).toList.traverse { header =>
      val elms = header.split(headersKeyValueDelim)
      if (elms.length != 2)
        BadSessionHeadersEncoding(header).asLeft
      else
        (elms(0) -> elms(1)).asRight
    }

  def requestConfigurationHandler[A: Show](req: HttpRequest[A], config: Config): Either[CornichonError, Done] = {
    if (config.traceRequests)
      println(DebugLogInstruction(req.show, 1).colorized)

    if (config.warnOnDuplicateHeaders && req.headers.groupBy(_._1).exists(_._2.size > 1))
      println(WarningLogInstruction(s"\n**Warning**\nduplicate headers detected in request:\n${req.show}", 1).colorized)

    if (config.failOnDuplicateHeaders && req.headers.groupBy(_._1).exists(_._2.size > 1))
      BasicError(s"duplicate headers detected in request:\n${req.show}").asLeft
    else
      Done.rightDone
  }

  def ignoreHeadersSelection(headers: Seq[(String, String)], ignore: HeaderSelection): Seq[(String, String)] =
    ignore match {
      case SelectNone     => headers
      case SelectAll      => Nil
      case ByNames(names) => headers.filterNot { case (n, _) => names.contains(n) }
    }

  def expectStatusCode(httpResponse: HttpResponse, expected: Option[Int], requestDescription: String): Either[CornichonError, HttpResponse] =
    expected match {
      case None =>
        httpResponse.asRight
      case Some(expectedStatus) if httpResponse.status == expectedStatus =>
        httpResponse.asRight
      case Some(expectedStatus) =>
        StatusNonExpected(expectedStatus, httpResponse.status, httpResponse.headers, httpResponse.body, requestDescription).asLeft
    }

  def fillInSessionWithResponse(session: Session, extractor: ResponseExtractor, requestDescription: String)(response: HttpResponse): Either[CornichonError, Session] = {
    val additionalExtractions = extractor match {
      case NoOpExtraction =>
        rightNil
      case RootExtractor(targetKey) =>
        Right((targetKey -> response.body) :: Nil)
      case PathExtractor(path, targetKey) =>
        JsonPath.runStrict(path, response.body)
          .map(extractedJson => (targetKey -> jsonStringValue(extractedJson)) :: Nil)
    }
    additionalExtractions.flatMap { extra =>
      val allElementsToAdd = commonSessionExtractions(response, requestDescription) ++ extra
      session.addValues(allElementsToAdd: _*)
    }
  }

  private def handleResponse(resp: HttpResponse, requestDescription: String, expectedStatus: Option[Int], extractor: ResponseExtractor)(session: Session): Either[CornichonError, Session] =
    expectStatusCode(resp, expectedStatus, requestDescription)
      .flatMap(fillInSessionWithResponse(session, extractor, requestDescription))

  private def commonSessionExtractions(response: HttpResponse, requestDescription: String): List[(String, String)] =
    (lastResponseStatusKey -> response.status.toString) ::
      (lastResponseBodyKey -> response.body) ::
      (lastResponseHeadersKey -> encodeSessionHeaders(response.headers)) ::
      (lastResponseRequestKey -> requestDescription) :: Nil
}