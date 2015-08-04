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

  def Post(payload: JsValue, url: String, expected: Option[StatusCode] = None)(s: Session)(implicit timeout: FiniteDuration): Xor[CornichonError, JsonHttpResponse] = {
    for {
      payloadResolved ← resolver.fillPlaceholder(payload)(s.content)
      urlResolved ← resolver.fillPlaceholder(url)(s.content)
      res ← Await.result(httpService.postJson(payloadResolved, urlResolved, expected), timeout)
    } yield {
      res
    }
  }

  def Put(payload: JsValue, url: String, expected: Option[StatusCode] = None)(s: Session)(implicit timeout: FiniteDuration): Xor[CornichonError, JsonHttpResponse] = {
    for {
      payloadResolved ← resolver.fillPlaceholder(payload)(s.content)
      urlResolved ← resolver.fillPlaceholder(url)(s.content)
      res ← Await.result(httpService.putJson(payloadResolved, urlResolved, expected), timeout)
    } yield {
      res
    }
  }

  def Get(url: String, expected: Option[StatusCode] = None)(s: Session)(implicit timeout: FiniteDuration): Xor[CornichonError, JsonHttpResponse] = {
    for {
      urlResolved ← resolver.fillPlaceholder(url)(s.content)
      res ← Await.result(httpService.getJson(urlResolved, expected), timeout)
    } yield {
      res
    }
  }

  def Delete(url: String, expected: Option[StatusCode] = None)(s: Session)(implicit timeout: FiniteDuration): Xor[CornichonError, JsonHttpResponse] = {
    for {
      urlResolved ← resolver.fillPlaceholder(url)(s.content)
      res ← Await.result(httpService.deleteJson(urlResolved, expected), timeout)
    } yield {
      res
    }
  }
}
