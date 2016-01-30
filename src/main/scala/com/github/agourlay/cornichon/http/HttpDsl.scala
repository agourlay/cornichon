package com.github.agourlay.cornichon.http

import com.github.agourlay.cornichon.CornichonFeature
import com.github.agourlay.cornichon.core.RunnableStep._
import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.dsl._
import com.github.agourlay.cornichon.dsl.Dsl._
import com.github.agourlay.cornichon.http.HttpAssertions._
import com.github.agourlay.cornichon.json.CornichonJson._
import com.github.agourlay.cornichon.json.JsonPath

import org.json4s._

import scala.concurrent.duration._

trait HttpDsl extends Dsl {
  this: CornichonFeature ⇒

  import HttpService._

  sealed trait Request {
    val name: String
  }

  sealed trait WithoutPayload extends Request {
    def apply(url: String, params: (String, String)*)(implicit headers: Seq[(String, String)] = Seq.empty) =
      effectful(
        title = {
        val base = s"$name $url"
        if (params.isEmpty) base
        else s"$base with params ${displayTuples(params)}"
      },
        effect =
        s ⇒ this match {
          case GET    ⇒ http.Get(url, params, headers)(s)
          case DELETE ⇒ http.Delete(url, params, headers)(s)
        }
      )
  }

  sealed trait WithPayload extends Request {
    def apply(url: String, payload: String, params: (String, String)*)(implicit headers: Seq[(String, String)] = Seq.empty) =
      effectful(
        title = {
        val base = s"$name to $url with payload $payload"
        if (params.isEmpty) base
        else s"$base with params ${displayTuples(params)}"
      },
        effect =
        s ⇒ this match {
          case POST ⇒ http.Post(url, payload, params, headers)(s)
          case PUT  ⇒ http.Put(url, payload, params, headers)(s)
        }
      )
  }

  sealed trait Streamed extends Request {
    def apply(url: String, takeWithin: FiniteDuration, params: (String, String)*)(implicit headers: Seq[(String, String)] = Seq.empty) =
      effectful(
        title = {
        val base = s"$name $url"
        if (params.isEmpty) base
        else s"$base with params ${displayTuples(params)}"
      },
        effect =
        s ⇒ this match {
          case GET_SSE ⇒ http.GetSSE(url, takeWithin, params, headers)(s)
          case GET_WS  ⇒ http.GetWS(url, takeWithin, params, headers)(s)
        }
      )
  }

  case object GET extends WithoutPayload {
    val name = "GET"
  }

  case object DELETE extends WithoutPayload {
    val name = "DELETE"
  }

  case object POST extends WithPayload {
    val name = "POST"
  }

  case object PUT extends WithPayload {
    val name = "PUT"
  }

  case object GET_SSE extends Streamed {
    val name = "GET SSE"
  }

  case object GET_WS extends Streamed {
    val name = "GET WS"
  }

  def WithHeaders(headers: (String, String)*) =
    BodyElementCollector[Step, Seq[Step]] { steps ⇒
      val saveStep = save(WithHeadersKey, headers.map { case (name, value) ⇒ s"$name$HeadersKeyValueDelim$value" }.mkString(",")).copy(show = false)
      val removeStep = remove(WithHeadersKey).copy(show = false)
      saveStep +: steps :+ removeStep
    }

  val root = JsonPath.root

  def status = StatusAssertion

  def headers = HeadersAssertion(ordered = false)

  def session_json_values(k1: String, k2: String) = SessionJsonValuesAssertion(k1, k2, Seq.empty)

  def body[A] = BodyAssertion[A](root, Seq.empty, whiteList = false, resolver)

  def bodyArray[A] = BodyArrayAssertion[A](root, ordered = false, Seq.empty, resolver)

  def save_body_key(args: (String, String)*) = {
    val inputs = args.map {
      case (key, t) ⇒ FromSessionSetter(LastResponseBodyKey, s ⇒ (parseJson(s) \ key).values.toString, t)
    }
    save_from_session(inputs)
  }

  def save_body_path(args: (JsonPath, String)*) = {
    val inputs = args.map {
      case (k, t) ⇒ FromSessionSetter(LastResponseBodyKey, s ⇒ selectJsonPath(k, s).values.toString, t)
    }
    save_from_session(inputs)
  }

  def show_last_status = show_session(LastResponseStatusKey)

  def show_last_response_body = show_session(LastResponseBodyKey)

  def show_last_response_body_as_json = show_key_as_json(LastResponseBodyKey)

  def show_last_response_headers = show_session(LastResponseHeadersKey)

  def show_key_as_json(key: String) = show_session(key, v ⇒ prettyPrint(parseJson(v)))
}