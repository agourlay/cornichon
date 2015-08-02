package com.github.agourlay.cornichon.dsl

import cats.data.Xor
import spray.json.JsValue
import scala.concurrent.duration._

import com.github.agourlay.cornichon.core.{ Session, CornichonError, Step }
import com.github.agourlay.cornichon.http._

trait HttpDsl extends Dsl {
  this: HttpFeature ⇒

  val LastResponseJsonKey = "last-response-json"
  val LastResponseStatusKey = "last-response-status"

  implicit val requestTimeout: FiniteDuration

  def GET(url: String, p: JsonHttpResponse ⇒ Boolean = _ ⇒ true) = {
    Step[Xor[CornichonError, JsonHttpResponse]](s"HTTP GET to $url",
      s ⇒ {
        val x = Get(url)(s)
        (x, x.fold(e ⇒ s, req ⇒ fillInSession(s, req)))
      },
      result ⇒ Xor2Predicate(result)(p(_))
    )
  }

  def POST(payload: String, url: String, p: JsonHttpResponse ⇒ Boolean = _ ⇒ true) = {
    Step[Xor[CornichonError, JsonHttpResponse]](s"HTTP POST to $url",
      s ⇒ {
        val x = Post(payload, url)(s)
        (x, x.fold(e ⇒ s, req ⇒ fillInSession(s, req)))
      },
      result ⇒ Xor2Predicate(result)(p(_))
    )
  }

  def fillInSession(session: Session, response: JsonHttpResponse): Session = {
    session.addValue(LastResponseStatusKey, response.status.intValue().toString)
      .addValue(LastResponseJsonKey, response.body.prettyPrint)
  }

  def status_is(status: Int) =
    assertSession(LastResponseStatusKey, status.toString)

  def showLastStatus =
    showSession(LastResponseStatusKey)

  def response_body_is(jsString: String) =
    assertSession(LastResponseJsonKey, jsString)

  def response_body_is(jsValue: JsValue) =
    assertSession(LastResponseJsonKey, jsValue.toString)

  def showLastReponseJson =
    showSession(LastResponseJsonKey)
}
