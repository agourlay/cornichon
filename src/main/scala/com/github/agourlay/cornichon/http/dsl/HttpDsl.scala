package com.github.agourlay.cornichon.http.dsl

import akka.http.scaladsl.model.HttpHeader
import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.core.dsl.Dsl
import com.github.agourlay.cornichon.http._
import spray.json._
import spray.json.DefaultJsonProtocol._

import scala.concurrent.duration._

trait HttpDsl extends Dsl {
  this: HttpFeature ⇒

  implicit val requestTimeout: FiniteDuration = 2000 millis

  sealed trait Request { val name: String }

  sealed trait WithoutPayload extends Request {
    def apply(url: String, params: (String, String)*)(implicit headers: Seq[HttpHeader] = Seq.empty) =
      Step(
        title = s"HTTP $name to $url",
        action = s ⇒ {
          val x = this match {
            case GET    ⇒ Get(url, params, headers)(s)
            case DELETE ⇒ Delete(url, params, headers)(s)
          }
          x.map { case (jsonRes, session) ⇒ (true, session) }.fold(e ⇒ throw e, identity)
        },
        expected = true)
  }

  sealed trait WithPayload extends Request {
    def apply(url: String, payload: JsValue, params: (String, String)*)(implicit headers: Seq[HttpHeader] = Seq.empty) =
      Step(
        title = s"HTTP $name to $url",
        action = s ⇒ {
          val x = this match {
            case POST ⇒ Post(payload, url, params, headers)(s)
            case PUT  ⇒ Put(payload, url, params, headers)(s)
          }
          x.map { case (jsonRes, session) ⇒ (true, session) }.fold(e ⇒ throw e, identity)
        },
        expected = true)
  }

  case object GET extends WithoutPayload { val name = "GET" }

  case object DELETE extends WithoutPayload { val name = "DELETE" }

  case object POST extends WithPayload { val name = "POST" }

  case object PUT extends WithPayload { val name = "PUT" }

  def status_is(status: Int) = session_contain(LastResponseStatusKey, status.toString)

  def headers_contain(headers: (String, String)*) =
    transformAndAssertSession(LastResponseHeadersKey, true, sessionHeaders ⇒ {
      val sessionHeadersValue = sessionHeaders.split(",")
      headers.forall { case (name, value) ⇒ sessionHeadersValue.contains(s"$name:$value") }
    })

  def showLastStatus = show_session(LastResponseStatusKey)

  def response_body_is(jsValue: JsValue, ignoredKeys: String*) =
    transformAndAssertSession(LastResponseJsonKey, jsValue, sessionValue ⇒ {
      if (ignoredKeys.isEmpty) sessionValue.parseJson
      else sessionValue.parseJson.asJsObject.fields.filterKeys(!ignoredKeys.contains(_)).toJson
    })

  def extractFromResponseBody(extractor: JsValue ⇒ String, target: String) = {
    extractFromSession(LastResponseJsonKey, s ⇒ extractor(s.parseJson), target)
  }

  def response_body_is(mapFct: JsValue ⇒ String, jsValue: String) =
    transformAndAssertSession(LastResponseJsonKey, jsValue, sessionValue ⇒ {
      mapFct(sessionValue.parseJson)
    })

  def showLastResponseJson = show_session(LastResponseJsonKey)

  def showLastResponseHeaders = show_session(LastResponseHeadersKey)

  def response_body_array_is[A](mapFct: JsArray ⇒ A, expected: A) = {
    transformAndAssertSession[A](LastResponseJsonKey, expected, sessionValue ⇒ {
      val sessionJSON = sessionValue.parseJson
      sessionJSON match {
        case arr: JsArray ⇒ mapFct(arr)
        case _            ⇒ throw new RuntimeException(s"Expected JSON Array but got $sessionJSON")
      }
    })
  }

  def response_body_array_size_is(size: Int) = response_body_array_is(_.elements.size, size)

  def response_body_array_contains(element: JsValue) = response_body_array_is(_.elements.contains(element), true)

  def response_body_array_not_contain(element: JsValue) = response_body_array_is(_.elements.contains(element), false)
}