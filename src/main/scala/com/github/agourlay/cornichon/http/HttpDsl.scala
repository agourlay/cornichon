package com.github.agourlay.cornichon.http

import java.nio.charset.StandardCharsets
import java.util.Base64

import com.github.agourlay.cornichon.CornichonFeature
import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.dsl._
import com.github.agourlay.cornichon.dsl.Dsl._
import com.github.agourlay.cornichon.http.HttpAssertions._
import com.github.agourlay.cornichon.http.HttpStreams._
import com.github.agourlay.cornichon.json.CornichonJson._
import com.github.agourlay.cornichon.json.JsonAssertions.JsonAssertion
import com.github.agourlay.cornichon.json.{ CornichonJson, JsonPath }
import com.github.agourlay.cornichon.steps.regular.EffectStep
import com.github.agourlay.cornichon.util.Formats
import io.circe.Json
import sangria.ast.Document
import sangria.renderer.QueryRenderer

import scala.concurrent.duration._

trait HttpDsl extends HttpRequestsDsl {
  this: CornichonFeature with Dsl ⇒

  import com.github.agourlay.cornichon.http.HttpService.SessionKeys._

  implicit def toStep[A: BodyInput](request: HttpRequest[A]): EffectStep =
    EffectStep(
      title = request.description,
      effect = http.requestEffect(request)
    )

  implicit def toStep(request: HttpStreamedRequest): EffectStep =
    EffectStep(
      title = request.description,
      effect = http.streamEffect(request)
    )

  def open_sse(url: String, takeWithin: FiniteDuration) = HttpStreamedRequest(SSE, url, takeWithin, Seq.empty, Seq.empty)

  implicit def toStep(queryGQL: QueryGQL): EffectStep = {
    import io.circe.generic.auto._
    import io.circe.syntax._

    // Used only for display - problem being that the query is a String and looks ugly inside the full JSON object.
    val payload = queryGQL.query.source.getOrElse(QueryRenderer.render(queryGQL.query, QueryRenderer.Pretty))

    val fullPayload = prettyPrint(GqlPayload(payload, queryGQL.operationName, queryGQL.variables).asJson)

    val prettyVar = queryGQL.variables.fold("") { variables ⇒
      " and with variables " + Formats.displayMap(variables, CornichonJson.prettyPrint)
    }

    val prettyOp = queryGQL.operationName.fold("")(o ⇒ s" and with operationName $o")

    EffectStep(
      title = s"query GraphQL endpoint ${queryGQL.url} with query $payload$prettyVar$prettyOp",
      effect = http.requestEffect(post(queryGQL.url).withBody(fullPayload))
    )
  }

  private case class GqlPayload(query: String, operationName: Option[String], variables: Option[Map[String, Json]])

  def query_gql(url: String) = QueryGQL(url, Document(List.empty))

  def status = StatusAssertion

  def headers = HeadersAssertion(ordered = false)

  //FIXME the body is expected to always contains JSON currently
  def body[A] = JsonAssertion[A](resolver, LastResponseBodyKey, Some("response body"))

  def save_body_path(args: (String, String)*) = {
    val inputs = args.map {
      case (path, target) ⇒ FromSessionSetter(LastResponseBodyKey, (session, s) ⇒ {
        val resolvedPath = resolver.fillPlaceholdersUnsafe(path)(session)
        JsonPath.parse(resolvedPath).run(s).fold(e ⇒ throw e, json ⇒ jsonStringValue(json))
      }, target)
    }
    save_from_session(inputs)
  }

  def show_last_status = show_session(LastResponseStatusKey)

  def show_last_response_body = show_session(LastResponseBodyKey)

  def show_last_response_body_as_json = show_key_as_json(LastResponseBodyKey)

  def show_last_response_headers = show_session(LastResponseHeadersKey)

  def show_key_as_json(key: String) = show_session(key, v ⇒ parseJson(v).fold(e ⇒ throw e, prettyPrint))

  def WithBasicAuth(userName: String, password: String) =
    WithHeaders(("Authorization", "Basic " + Base64.getEncoder.encodeToString(s"$userName:$password".getBytes(StandardCharsets.UTF_8))))

  def WithHeaders(headers: (String, String)*) =
    BodyElementCollector[Step, Seq[Step]] { steps ⇒
      val saveStep = save((WithHeadersKey, headers.map { case (name, value) ⇒ s"$name$HeadersKeyValueDelim$value" }.mkString(","))).copy(show = false)
      val removeStep = remove(WithHeadersKey).copy(show = false)
      saveStep +: steps :+ removeStep
    }
}