package com.github.agourlay.cornichon.http

import java.nio.charset.StandardCharsets
import java.util.Base64

import com.github.agourlay.cornichon.CornichonFeature
import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.dsl._
import com.github.agourlay.cornichon.dsl.Dsl._
import com.github.agourlay.cornichon.http.HttpAssertions._
import com.github.agourlay.cornichon.http.HttpEffects._
import com.github.agourlay.cornichon.json.CornichonJson._
import com.github.agourlay.cornichon.json.JsonPath
import com.github.agourlay.cornichon.steps.regular.EffectStep
import sangria.ast.Document

import scala.concurrent.duration._

trait HttpDsl extends Dsl {
  this: CornichonFeature ⇒

  import com.github.agourlay.cornichon.http.HttpService._

  implicit def toStep(request: HttpRequest): EffectStep = EffectStep(
    title = request.description,
    effect = s ⇒
    request match {
      case Get(url, params, headers)                   ⇒ http.Get(url, params, headers)(s)
      case Head(url, params, headers)                  ⇒ http.Head(url, params, headers)(s)
      case Options(url, params, headers)               ⇒ http.Options(url, params, headers)(s)
      case Delete(url, params, headers)                ⇒ http.Delete(url, params, headers)(s)
      case Post(url, payload, params, headers)         ⇒ http.Post(url, payload, params, headers)(s)
      case Put(url, payload, params, headers)          ⇒ http.Put(url, payload, params, headers)(s)
      case Patch(url, payload, params, headers)        ⇒ http.Patch(url, payload, params, headers)(s)
      case OpenSSE(url, takeWithin, params, headers)   ⇒ http.OpenSSE(url, takeWithin, params, headers)(s)
      case OpenWS(url, takeWithin, params, headers)    ⇒ http.OpenWS(url, takeWithin, params, headers)(s)
      case q @ QueryGQL(url, params, headers, _, _, _) ⇒ http.Post(url, q.payload, params, headers)(s)
    }
  )

  def get(url: String) = Get(url, Seq.empty, Seq.empty)
  def delete(url: String) = Delete(url, Seq.empty, Seq.empty)

  def post(url: String, payload: String) = Post(url, payload, Seq.empty, Seq.empty)
  def put(url: String, payload: String) = Put(url, payload, Seq.empty, Seq.empty)

  def open_sse(url: String, takeWithin: FiniteDuration) = OpenSSE(url, takeWithin, Seq.empty, Seq.empty)
  def open_ws(url: String, takeWithin: FiniteDuration) = OpenWS(url, takeWithin, Seq.empty, Seq.empty)

  def query_gql(url: String) = QueryGQL(url, Seq.empty, Seq.empty, Document(List.empty))

  val root = JsonPath.root

  def status = StatusAssertion

  def headers = HeadersAssertion(ordered = false)

  def session_json_values(k1: String, k2: String) = SessionJsonValuesAssertion(k1, k2, Seq.empty, resolver)

  def body[A] = BodyAssertion[A](root, Seq.empty, whiteList = false, resolver)

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

  def show_key_as_json(key: String) = show_session(key, v ⇒ parseJson(v).fold(e ⇒ throw e, prettyPrint(_)))

  def WithBasicAuth(userName: String, password: String) =
    WithHeaders(("Authorization", "Basic " + Base64.getEncoder.encodeToString(s"$userName:$password".getBytes(StandardCharsets.UTF_8))))

  def WithHeaders(headers: (String, String)*) =
    BodyElementCollector[Step, Seq[Step]] { steps ⇒
      val saveStep = save((WithHeadersKey, headers.map { case (name, value) ⇒ s"$name$HeadersKeyValueDelim$value" }.mkString(","))).copy(show = false)
      val removeStep = remove(WithHeadersKey).copy(show = false)
      saveStep +: steps :+ removeStep
    }
}