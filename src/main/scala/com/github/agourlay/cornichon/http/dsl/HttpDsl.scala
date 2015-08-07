package com.github.agourlay.cornichon.http.dsl

import akka.http.scaladsl.model.{ HttpHeader, StatusCode }
import cats.data.Xor
import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.core.dsl.Dsl
import com.github.agourlay.cornichon.http._
import spray.json.{ JsValue, _ }
import spray.json.DefaultJsonProtocol._

import scala.collection.immutable
import scala.concurrent.duration._

trait HttpDsl extends Dsl {
  this: HttpFeature ⇒

  implicit val requestTimeout: FiniteDuration = 2000 millis

  private def xor2A[A](x: Xor[CornichonError, (A, Session)], formerSession: Session) =
    (x.fold(e ⇒ throw e, res ⇒ res._1), x.fold(e ⇒ formerSession, res ⇒ res._2))

  sealed trait Request { val name: String }

  sealed trait WithoutPayload extends Request {
    def apply[A](url: String, mapFct: JsonHttpResponse ⇒ A, expected: A = true,
                 params: Map[String, String] = Map.empty, headers: immutable.Seq[HttpHeader] = immutable.Seq.empty): Step[A] =
      Step(
        title = s"HTTP $name to $url",
        action = s ⇒ {
          val x = this match {
            case GET    ⇒ Get(url, params, headers)(s).map { case (response, session) ⇒ (mapFct(response), session) }
            case DELETE ⇒ Delete(url, params, headers)(s).map { case (response, session) ⇒ (mapFct(response), session) }
          }
          xor2A(x, s)
        },
        expected = expected)

    def apply(url: String): Step[Boolean] = apply(url, _ ⇒ true)
    def apply(url: String, withParams: Map[String, String]) = apply[Boolean](url, _ ⇒ true, true, withParams)
    def apply(url: String, expectedHeaders: Seq[HttpHeader]) = apply[Boolean](url, r ⇒ expectedHeaders.forall(r.headers.contains(_)))
    def apply(url: String, expectedBody: JsValue) = apply[JsValue](url, _.body, expectedBody)
    def apply(url: String, expectedStatusCode: StatusCode) = apply[StatusCode](url, _.status, expectedStatusCode)
  }

  sealed trait WithPayload extends Request {
    def apply[A](url: String, payload: JsValue, mapFct: JsonHttpResponse ⇒ A, expected: A = true,
                 params: Map[String, String] = Map.empty, headers: immutable.Seq[HttpHeader] = immutable.Seq.empty): Step[A] =
      Step(
        title = s"HTTP $name to $url",
        action = s ⇒ {
          val x = this match {
            case POST ⇒ Post(payload, url, params, headers)(s).map { case (response, session) ⇒ (mapFct(response), session) }
            case PUT  ⇒ Put(payload, url, params, headers)(s).map { case (response, session) ⇒ (mapFct(response), session) }
          }
          xor2A(x, s)
        },
        expected = expected)

    def apply(url: String, payload: JsValue): Step[Boolean] = apply(url, payload, _ ⇒ true)
    def apply(url: String, payload: JsValue, withParams: Map[String, String]) = apply[Boolean](url, payload, _ ⇒ true, true, withParams)
    def apply(url: String, payload: JsValue, expectedHeaders: Seq[HttpHeader]) = apply[Boolean](url, payload, r ⇒ expectedHeaders.forall(r.headers.contains(_)))
    def apply(url: String, payload: JsValue, expectedBody: JsValue) = apply[JsValue](url, payload, _.body, expectedBody)
    def apply(url: String, payload: JsValue, expectedStatusCode: StatusCode) = apply[StatusCode](url, payload, _.status, expectedStatusCode)
  }

  case object GET extends WithoutPayload { val name = "GET" }

  case object DELETE extends WithoutPayload { val name = "DELETE" }

  case object POST extends WithPayload { val name = "POST" }

  case object PUT extends WithPayload { val name = "PUT" }

  def status_is(status: Int) = assertSession(LastResponseStatusKey, status.toString)

  def showLastStatus = showSession(LastResponseStatusKey)

  def response_body_is(jsValue: JsValue, ignoredKeys: Seq[String] = Seq.empty) =
    assertSessionWithMap(LastResponseJsonKey, jsValue, sessionValue ⇒ {
      if (ignoredKeys.isEmpty) sessionValue.parseJson
      else sessionValue.parseJson.asJsObject.fields.filterKeys(!ignoredKeys.contains(_)).toJson
    })

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