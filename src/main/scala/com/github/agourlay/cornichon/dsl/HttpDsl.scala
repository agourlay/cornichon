package com.github.agourlay.cornichon.dsl

import akka.http.scaladsl.model.StatusCode
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

  // TODO abstract request function boilerplate with traits Method, Read/Write
  def GET(url: String): Step[Boolean] = GET(url, _ ⇒ true, true)
  def GET(url: String, expectedBody: JsValue) = GET[JsValue](url, _.body, expectedBody)
  def GET(url: String, expectedStatusCode: StatusCode) = GET[StatusCode](url, _.status, expectedStatusCode)
  def GET[A](url: String, mapFct: JsonHttpResponse ⇒ A, expected: A): Step[A] = {
    Step(
      title = s"HTTP GET to $url",
      action = s ⇒ {
        val x = Get(url)(s).map { case (response, session) ⇒ (mapFct(response), session) }
        xor2A(x, s)
      },
      expected = expected)
  }

  def DELETE(url: String): Step[Boolean] = DELETE(url, _ ⇒ true, true)
  def DELETE(url: String, expectedBody: JsValue) = DELETE[JsValue](url, _.body, expectedBody)
  def DELETE(url: String, expectedStatusCode: StatusCode) = DELETE[StatusCode](url, _.status, expectedStatusCode)
  def DELETE[A](url: String, mapFct: JsonHttpResponse ⇒ A, expected: A): Step[A] = {
    Step(
      title = s"HTTP DELETE to $url",
      action = s ⇒ {
        val x = Delete(url)(s).map { case (response, session) ⇒ (mapFct(response), session) }
        xor2A(x, s)
      },
      expected = expected)
  }

  def POST(url: String, payload: JsValue): Step[Boolean] = POST(url, payload, _ ⇒ true, true)
  def POST(url: String, payload: JsValue, expectedBody: JsValue) = POST[JsValue](url, payload, _.body, expectedBody)
  def POST(url: String, payload: JsValue, expectedStatusCode: StatusCode) = POST[StatusCode](url, payload, _.status, expectedStatusCode)
  def POST[A](url: String, payload: JsValue, mapFct: JsonHttpResponse ⇒ A, expected: A): Step[A] = {
    Step(
      title = s"HTTP POST to $url",
      action = s ⇒ {
        val x = Post(payload, url)(s).map { case (response, session) ⇒ (mapFct(response), session) }
        xor2A(x, s)
      },
      expected = expected)
  }

  def PUT(url: String, payload: JsValue): Step[Boolean] = PUT(url, payload, _ ⇒ true, true)
  def PUT(url: String, payload: JsValue, expectedBody: JsValue) = PUT[JsValue](url, payload, _.body, expectedBody)
  def PUT(url: String, payload: JsValue, expectedStatusCode: StatusCode) = PUT[StatusCode](url, payload, _.status, expectedStatusCode)
  def PUT[A](url: String, payload: JsValue, mapFct: JsonHttpResponse ⇒ A, expected: A): Step[A] = {
    Step(
      title = s"HTTP PUT to $url",
      action = s ⇒ {
        val x = Put(payload, url)(s).map { case (response, session) ⇒ (mapFct(response), session) }
        xor2A(x, s)
      },
      expected = expected)
  }

  def status_is(status: Int) =
    assertSession(LastResponseStatusKey, status.toString)

  def showLastStatus = showSession(LastResponseStatusKey)

  def response_body_is(jsValue: JsValue) =
    assertSessionWithMap(LastResponseJsonKey, jsValue, sessionValue ⇒ sessionValue.parseJson)

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
}