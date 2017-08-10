package com.github.agourlay.cornichon.http

import java.nio.charset.StandardCharsets
import java.util.Base64

import cats.Show
import cats.syntax.show._

import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.dsl._
import com.github.agourlay.cornichon.dsl.Dsl._
import com.github.agourlay.cornichon.feature.BaseFeature
import com.github.agourlay.cornichon.http.steps.HeadersSteps._
import com.github.agourlay.cornichon.http.HttpStreams._
import com.github.agourlay.cornichon.json.CornichonJson._
import com.github.agourlay.cornichon.json.JsonSteps.JsonStepBuilder
import com.github.agourlay.cornichon.json.JsonPath
import com.github.agourlay.cornichon.resolver.Resolvable
import com.github.agourlay.cornichon.steps.regular.{ DebugStep, EffectStep }
import com.github.agourlay.cornichon.http.HttpService.SessionKeys._
import com.github.agourlay.cornichon.http.HttpService._
import com.github.agourlay.cornichon.http.steps.StatusSteps._
import com.github.agourlay.cornichon.util.Printing._

import io.circe.{ Encoder, Json }

import sangria.ast.Document

import scala.concurrent.duration._

trait HttpDsl extends HttpRequestsDsl {
  this: BaseFeature with Dsl ⇒

  implicit def httpRequestToStep[A: Show: Resolvable: Encoder](request: HttpRequest[A]): EffectStep =
    EffectStep(
      title = request.compactDescription,
      effect = http.requestEffect(request)
    )

  implicit def httpStreamedRequestToStep(request: HttpStreamedRequest): EffectStep =
    EffectStep(
      title = request.compactDescription,
      effect = http.streamEffect(request)
    )

  implicit def queryGqlToStep(queryGQL: QueryGQL): EffectStep = {
    // Used only for display - problem being that the query is a String and looks ugly inside the full JSON object.
    val prettyPayload = queryGQL.querySource

    val prettyVar = queryGQL.variables.fold("") { variables ⇒
      " and with variables " + variables.show
    }

    val prettyOp = queryGQL.operationName.fold("")(o ⇒ s" and with operationName $o")

    EffectStep(
      title = s"query GraphQL endpoint ${queryGQL.url} with query $prettyPayload$prettyVar$prettyOp",
      effect = http.requestEffect(queryGQL)
    )
  }

  implicit def queryGqlToHttpRequest(queryGQL: QueryGQL): HttpRequest[String] =
    post(queryGQL.url).withBody(queryGQL.payload)

  def query_gql(url: String) = QueryGQL(url, Document(Vector.empty))

  def open_sse(url: String, takeWithin: FiniteDuration) = HttpStreamedRequest(SSE, url, takeWithin, Seq.empty, Seq.empty)

  def status = StatusStepBuilder

  def headers = HeadersStepBuilder(ordered = false)

  //FIXME the body is expected to always contains JSON currently
  def body = JsonStepBuilder(resolver, SessionKey(lastResponseBodyKey), Some("response body"))

  def save_body(target: String) = save_body_path(JsonPath.root → target)

  def save_body_path(args: (String, String)*) = {
    val inputs = args.map {
      case (path, target) ⇒ FromSessionSetter(lastResponseBodyKey, (session, s) ⇒ {
        for {
          resolvedPath ← resolver.fillPlaceholders(path)(session)
          jsonPath ← JsonPath.parse(resolvedPath)
          json ← jsonPath.run(s)
        } yield jsonStringValue(json)
      }, target)
    }
    save_from_session(inputs)
  }

  def save_header_value(args: (String, String)*) = {
    val inputs = args.map {
      case (headerFieldname, target) ⇒ FromSessionSetter(lastResponseHeadersKey, (session, s) ⇒ {
        decodeSessionHeaders(s).map(_.find(_._1 == headerFieldname).map(h ⇒ h._2).getOrElse(""))
      }, target)
    }
    save_from_session(inputs)
  }

  private def showLastReponse[A: Show](parse: String ⇒ Either[CornichonError, A]) = DebugStep(s ⇒
    for {
      headers ← s.get(lastResponseHeadersKey)
      decodedHeaders ← decodeSessionHeaders(headers)
      status ← s.get(lastResponseStatusKey)
      body ← s.get(lastResponseBodyKey)
      bodyParsed ← parse(body)
    } yield {
      s"""Show last response
         |headers: ${printArrowPairs(decodedHeaders)}
         |status : $status
         |body   : ${bodyParsed.show}
     """.stripMargin
    })

  def show_last_response = showLastReponse(b ⇒ Right(b))
  def show_last_response_json = showLastReponse[Json](parseJson)
  def show_last_status = show_session(lastResponseStatusKey)

  def show_last_body = show_session(lastResponseBodyKey)
  def show_last_body_json = show_key_as_json(lastResponseBodyKey)

  def show_last_headers = show_session(lastResponseHeadersKey)

  def WithBasicAuth(userName: String, password: String) =
    WithHeaders(("Authorization", "Basic " + Base64.getEncoder.encodeToString(s"$userName:$password".getBytes(StandardCharsets.UTF_8))))

  def addToWithHeaders(name: String, value: String)(s: Session) = {
    val currentHeader = s.getOpt(withHeadersKey).fold("")(v ⇒ s"$v$interHeadersValueDelim")
    s.addValue("with-headers", s"$currentHeader${encodeSessionHeader(name, value)}")
  }

  //FIXME do not destroy the content of 'withHeadersKey' afterwards but restore it to its first value
  def WithHeaders(headers: (String, String)*) =
    BodyElementCollector[Step, Seq[Step]] { steps ⇒
      val saveStep = save((withHeadersKey, headers.map { case (name, value) ⇒ s"$name$headersKeyValueDelim$value" }.mkString(","))).copy(show = false)
      val removeStep = remove(withHeadersKey).copy(show = false)
      saveStep +: steps :+ removeStep
    }
}