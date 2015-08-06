package com.github.agourlay.cornichon.http

import akka.actor.ActorSystem
import akka.http.scaladsl.model.StatusCode
import akka.stream.ActorMaterializer
import cats.data.Xor
import com.github.agourlay.cornichon.core._
import spray.json._

import scala.concurrent.duration._
import scala.concurrent.{ Await, ExecutionContext }

trait HttpFeature extends Feature {

  implicit val system = ActorSystem("cornichon-http-feature")
  implicit val mat = ActorMaterializer()
  implicit val ec: ExecutionContext = system.dispatcher
  val httpService = new HttpService

  val LastResponseJsonKey = "last-response-json"
  val LastResponseStatusKey = "last-response-status"

  def Post(payload: JsValue, url: String)(s: Session)(implicit timeout: FiniteDuration): Xor[CornichonError, (JsonHttpResponse, Session)] = {
    for {
      payloadResolved ← resolver.fillPlaceholder(payload)(s.content)
      urlResolved ← resolver.fillPlaceholder(url)(s.content)
      res ← Await.result(httpService.postJson(payloadResolved, urlResolved), timeout)
      newSession = fillInHttpSession(s, res)
    } yield {
      (res, newSession)
    }
  }

  def Put(payload: JsValue, url: String)(s: Session)(implicit timeout: FiniteDuration): Xor[CornichonError, (JsonHttpResponse, Session)] = {
    for {
      payloadResolved ← resolver.fillPlaceholder(payload)(s.content)
      urlResolved ← resolver.fillPlaceholder(url)(s.content)
      res ← Await.result(httpService.putJson(payloadResolved, urlResolved), timeout)
      newSession = fillInHttpSession(s, res)
    } yield {
      (res, newSession)
    }
  }

  def Get(url: String)(s: Session)(implicit timeout: FiniteDuration): Xor[CornichonError, (JsonHttpResponse, Session)] = {
    for {
      urlResolved ← resolver.fillPlaceholder(url)(s.content)
      res ← Await.result(httpService.getJson(urlResolved), timeout)
      newSession = fillInHttpSession(s, res)
    } yield {
      (res, newSession)
    }
  }

  def Delete(url: String)(s: Session)(implicit timeout: FiniteDuration): Xor[CornichonError, (JsonHttpResponse, Session)] = {
    for {
      urlResolved ← resolver.fillPlaceholder(url)(s.content)
      res ← Await.result(httpService.deleteJson(urlResolved), timeout)
      newSession = fillInHttpSession(s, res)
    } yield {
      (res, newSession)
    }
  }

  def fillInHttpSession(session: Session, response: JsonHttpResponse): Session = {
    session.addValue(LastResponseStatusKey, response.status.intValue().toString)
      .addValue(LastResponseJsonKey, response.body.prettyPrint)
  }

}
