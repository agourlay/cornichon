package com.github.agourlay.cornichon.http

import cats.Show
import cats.syntax.show._
import cats.syntax.either._
import cats.syntax.traverse._
import cats.instances.list._
import cats.instances.either._
import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.dsl._
import com.github.agourlay.cornichon.dsl.CoreDsl._
import com.github.agourlay.cornichon.http.HttpDsl._
import com.github.agourlay.cornichon.http.steps.HeadersSteps._
import com.github.agourlay.cornichon.http.HttpStreams._
import com.github.agourlay.cornichon.json.CornichonJson._
import com.github.agourlay.cornichon.json.JsonSteps.JsonStepBuilder
import com.github.agourlay.cornichon.json.{ JsonDsl, JsonPath }
import com.github.agourlay.cornichon.resolver.Resolvable
import com.github.agourlay.cornichon.steps.regular.{ DebugStep, EffectStep }
import com.github.agourlay.cornichon.steps.cats.{ EffectStep => CEffectStep }
import com.github.agourlay.cornichon.http.HttpService.SessionKeys._
import com.github.agourlay.cornichon.http.HttpService._
import com.github.agourlay.cornichon.http.client.{ Http4sClient, HttpClient }
import com.github.agourlay.cornichon.http.steps.{ HeadersSteps, StatusSteps }
import com.github.agourlay.cornichon.http.steps.StatusSteps._
import com.github.agourlay.cornichon.util.Printing._

import io.circe.{ Encoder, Json }

import java.nio.charset.StandardCharsets
import java.util.Base64

import monix.execution.Scheduler

import scala.concurrent.duration._

trait HttpDsl extends HttpDslOps with HttpRequestsDsl {
  this: BaseFeature with JsonDsl with CoreDsl =>

  lazy val requestTimeout: FiniteDuration = config.requestTimeout
  lazy val baseUrl: String = config.globalBaseUrl

  def httpServiceByURL(baseUrl: String, timeout: FiniteDuration = requestTimeout) =
    new HttpService(baseUrl, timeout, HttpDsl.globalHttpClient, config)

  lazy val http: HttpService = httpServiceByURL(baseUrl, requestTimeout)

  implicit def httpRequestToStep[A: Show: Resolvable: Encoder](request: HttpRequest[A]): Step =
    CEffectStep(
      title = request.compactDescription,
      effect = http.requestEffectTask(request)
    )

  implicit def httpStreamedRequestToStep(request: HttpStreamedRequest): Step =
    EffectStep(
      title = request.compactDescription,
      effect = http.streamEffect(request)
    )

  implicit def queryGqlToStep(queryGQL: QueryGQL): Step = {
    // Used only for display - problem being that the query is a String and looks ugly inside the full JSON object.
    val prettyPayload = queryGQL.querySource

    val prettyVar = queryGQL.variables.fold("") { variables =>
      " and with variables " + variables.show
    }

    val prettyOp = queryGQL.operationName.fold("")(o => s" and with operationName $o")

    CEffectStep(
      title = s"query GraphQL endpoint ${queryGQL.url} with query $prettyPayload$prettyVar$prettyOp",
      effect = http.requestEffectTask(queryGQL)
    )
  }

  implicit def queryGqlToHttpRequest(queryGQL: QueryGQL): HttpRequest[String] =
    post(queryGQL.url)
      .withBody(queryGQL.payload)
      .withParams(queryGQL.params: _*)
      .withHeaders(queryGQL.headers: _*)

  def query_gql(url: String): QueryGQL =
    QueryGQL(url, QueryGQL.emptyDocument, None, None, Nil, Nil)

  def open_sse(url: String, takeWithin: FiniteDuration): HttpStreamedRequest =
    HttpStreamedRequest(SSE, url, takeWithin, Nil, Nil)

  def status: StatusSteps.StatusStepBuilder.type =
    StatusStepBuilder

  def headers: HeadersSteps.HeadersStepBuilder.type =
    HeadersStepBuilder

  //FIXME the body is expected to always contains JSON currently
  private lazy val jsonStepBuilder = JsonStepBuilder(HttpDsl.lastBodySessionKey, HttpDsl.bodyBuilderTitle)
  def body: JsonStepBuilder = jsonStepBuilder

  def save_body(target: String): Step = HttpDsl.save_body(target)

  def save_body_path(args: (String, String)*): Step =
    save_many_from_session_json(lastResponseBodyKey) {
      args.map {
        case (path, target) =>
          FromSessionSetter[Json](target, s"save path '$path' from body to key '$target'", (sc, jsonSessionValue) => {
            for {
              resolvedPath <- sc.fillPlaceholders(path)
              jsonPath <- JsonPath.parse(resolvedPath)
              json <- jsonPath.runStrict(jsonSessionValue)
            } yield jsonStringValue(json)
          })
      }
    }

  def save_header_value(args: (String, String)*): Step =
    save_many_from_session(lastResponseHeadersKey) {
      args.map {
        case (headerFieldName, target) =>
          FromSessionSetter(target, s"save '$headerFieldName' header value to key '$target'", (_, s: String) => {
            decodeSessionHeaders(s).map(_.find(_._1 == headerFieldName).map(h => h._2).getOrElse(""))
          })
      }
    }

  private def showLastResponse[A: Show](title: String)(parse: String => Either[CornichonError, A]) =
    DebugStep(
      title = title,
      message = sc => {
        val s = sc.session
        for {
          headers <- s.get(lastResponseHeadersKey)
          decodedHeaders <- decodeSessionHeaders(headers)
          status <- s.get(lastResponseStatusKey)
          body <- s.get(lastResponseBodyKey)
          bodyParsed <- parse(body)
        } yield {
          s"""Show last response
             |headers: ${printArrowPairs(decodedHeaders)}
             |status : $status
             |body   : ${bodyParsed.show}
         """.stripMargin
        }
      }
    )

  def show_last_response: Step = showLastResponse("show last response")(_.asRight)
  def show_last_response_json: Step = showLastResponse[Json]("show last response JSON")(parseString)
  def show_last_status: Step = show_session(lastResponseStatusKey)

  def show_last_body: Step = show_session(lastResponseBodyKey)
  def show_last_body_json: Step = show_key_as_json(lastResponseBodyKey)

  def show_last_headers: Step = show_session(lastResponseHeadersKey)
  def show_with_headers: Step = show_session(withHeadersKey)

  def WithBasicAuth(userName: String, password: String): BodyElementCollector[Step, Seq[Step]] =
    WithHeaders(("Authorization", "Basic " + Base64.getEncoder.encodeToString(s"$userName:$password".getBytes(StandardCharsets.UTF_8))))

  def WithHeaders(headers: (String, String)*): BodyElementCollector[Step, Seq[Step]] =
    BodyElementCollector[Step, Seq[Step]] { steps =>
      // the surrounding steps are hidden from the logs
      val saveStep = save((withHeadersKey, encodeSessionHeaders(headers)), show = false)
      val rollbackStep = rollback(withHeadersKey, show = false)
      saveStep +: steps :+ rollbackStep
    }
}

// Utils not building Steps
trait HttpDslOps {

  def addToWithHeaders(name: String, value: String)(s: Session): Either[CornichonError, Session] = {
    val currentHeader = s.getOpt(withHeadersKey).fold("")(v => s"$v$interHeadersValueDelim")
    s.addValue(withHeadersKey, s"$currentHeader${encodeSessionHeader(name, value)}")
  }

  def removeFromWithHeaders(name: String)(s: Session): Either[CornichonError, Session] =
    s.getOpt(withHeadersKey)
      .fold(s.asRight[CornichonError]) { currentHeadersString =>
        if (currentHeadersString.trim.isEmpty)
          s.asRight
        else
          decodeSessionHeaders(currentHeadersString).flatMap { ch =>
            val (dump, keep) = ch.partition(_._1 == name)
            if (dump.isEmpty)
              s.asRight
            else if (keep.isEmpty)
              s.removeKey(withHeadersKey).asRight
            else
              s.addValue(withHeadersKey, encodeSessionHeaders(keep))
          }
      }
}

object HttpDsl {
  val lastBodySessionKey = SessionKey(lastResponseBodyKey)
  val bodyBuilderTitle = Some("response body")

  def save_many_from_session_json(fromKey: String)(args: Seq[FromSessionSetter[Json]]): Step =
    CEffectStep.fromSyncE(
      s"${args.iterator.map(_.title).mkString(" and ")}",
      sc => {
        val session = sc.session
        for {
          sessionValue <- session.getJson(fromKey)
          extracted <- args.iterator.map(_.trans).toList.traverse { extractor => extractor(sc, sessionValue) }
          newSession <- args.iterator.map(_.target).zip(extracted.iterator).foldLeft(Either.right[CornichonError, Session](session))((s, tuple) => s.flatMap(_.addValue(tuple._1, tuple._2)))
        } yield newSession
      }
    )

  def save_body(target: String): Step =
    EffectStep.fromSyncE(
      title = s"save body to key '$target'",
      effect = sc => {
        for {
          sessionValue <- sc.session.get(lastResponseBodyKey)
          newSession <- sc.session.addValue(target, sessionValue)
        } yield newSession
      }
    )

  lazy val globalHttpClient: HttpClient = {
    val config = BaseFeature.config
    val c = new Http4sClient(config.addAcceptGzipByDefault, config.disableCertificateVerification, config.followRedirect)(Scheduler.Implicits.global)
    BaseFeature.addShutdownHook(() => c.shutdown().runToFuture(Scheduler.Implicits.global))
    c
  }
}