package com.github.agourlay.cornichon.dsl

import akka.http.scaladsl.model.{ HttpHeader, StatusCode }
import cats.data.Xor
import spray.json.JsValue
import scala.concurrent.duration._

import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.http._

import spray.json._

trait HttpDsl extends Dsl {
  this: HttpFeature ⇒

  implicit val requestTimeout: FiniteDuration

  private def xor2A[A](x: Xor[CornichonError, (A, Session)], formerSession: Session) =
    (x.fold(e ⇒ throw e, res ⇒ res._1), x.fold(e ⇒ formerSession, res ⇒ res._2))

  sealed trait Request { val name: String }

  sealed trait WithoutPayload extends Request {
    def apply[A](url: String, mapFct: JsonHttpResponse ⇒ A, expected: A): Step[A] =
      Step(
        title = s"HTTP $name to $url",
        action = s ⇒ {
          val x = this match {
            case GET    ⇒ Get(url)(s).map { case (response, session) ⇒ (mapFct(response), session) }
            case DELETE ⇒ Delete(url)(s).map { case (response, session) ⇒ (mapFct(response), session) }
          }
          xor2A(x, s)
        },
        expected = expected)

    def apply(url: String): Step[Boolean] = apply(url, _ ⇒ true, true)
    def apply(url: String, expectedHeaders: Seq[HttpHeader]) = apply[Boolean](url, r ⇒ expectedHeaders.forall(r.headers.contains(_)), true)
    def apply(url: String, expectedBody: JsValue) = apply[JsValue](url, _.body, expectedBody)
    def apply(url: String, expectedStatusCode: StatusCode) = apply[StatusCode](url, _.status, expectedStatusCode)
  }

  sealed trait WithPayload extends Request {
    def apply[A](url: String, payload: JsValue, mapFct: JsonHttpResponse ⇒ A, expected: A): Step[A] =
      Step(
        title = s"HTTP $name to $url",
        action = s ⇒ {
          val x = this match {
            case POST ⇒ Post(payload, url)(s).map { case (response, session) ⇒ (mapFct(response), session) }
            case PUT  ⇒ Put(payload, url)(s).map { case (response, session) ⇒ (mapFct(response), session) }
          }
          xor2A(x, s)
        },
        expected = expected)

    def apply(url: String, payload: JsValue): Step[Boolean] = apply(url, payload, _ ⇒ true, true)
    def apply(url: String, payload: JsValue, expectedHeaders: Seq[HttpHeader]) = apply[Boolean](url, payload, r ⇒ expectedHeaders.forall(r.headers.contains(_)), true)
    def apply(url: String, payload: JsValue, expectedBody: JsValue) = apply[JsValue](url, payload, _.body, expectedBody)
    def apply(url: String, payload: JsValue, expectedStatusCode: StatusCode) = apply[StatusCode](url, payload, _.status, expectedStatusCode)
  }

  case object GET extends WithoutPayload { val name = "GET" }

  case object DELETE extends WithoutPayload { val name = "DELETE" }

  case object POST extends WithPayload { val name = "POST" }

  case object PUT extends WithPayload { val name = "PUT" }

  def status_is(status: Int) = assertSession(LastResponseStatusKey, status.toString)

  def showLastStatus = showSession(LastResponseStatusKey)

  def response_body_is(jsValue: JsValue) = assertSessionWithMap(LastResponseJsonKey, jsValue, sessionValue ⇒ sessionValue.parseJson)

  def showLastResponseJson = showSession(LastResponseJsonKey)

  def response_body_array_is[A](mapFct: JsArray ⇒ A, expected: A) = {
    assertSessionWithMap[A](LastResponseJsonKey, expected, sessionValue ⇒ {
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