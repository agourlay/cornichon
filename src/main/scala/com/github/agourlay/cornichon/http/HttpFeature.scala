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

  def Post(payload: String, url: String, expected: Option[StatusCode] = None)(s: Session)(implicit timeout: FiniteDuration): Xor[CornichonError, JsonHttpResponse] = {
    for {
      payloadResolved ← resolver.fillPlaceHolder(payload)(s.content)
      urlResolved ← resolver.fillPlaceHolder(url)(s.content)
      jsPayload = payloadResolved.parseJson.asJsObject
      res ← Await.result(httpService.postJson(jsPayload, urlResolved, expected), timeout)
    } yield {
      res
    }
  }

  def Put(payload: String, url: String, expected: Option[StatusCode] = None)(s: Session)(implicit timeout: FiniteDuration): Xor[CornichonError, JsonHttpResponse] = {
    for {
      payloadResolved ← resolver.fillPlaceHolder(payload)(s.content)
      urlResolved ← resolver.fillPlaceHolder(url)(s.content)
      jsPayload = payloadResolved.parseJson.asJsObject
      res ← Await.result(httpService.putJson(jsPayload, urlResolved, expected), timeout)
    } yield {
      res
    }
  }

  def Get(url: String, expected: Option[StatusCode] = None)(s: Session)(implicit timeout: FiniteDuration): Xor[CornichonError, JsonHttpResponse] = {
    for {
      urlResolved ← resolver.fillPlaceHolder(url)(s.content)
      res ← Await.result(httpService.getJson(urlResolved, expected), timeout)
    } yield {
      res
    }
  }

  def Delete(url: String, expected: Option[StatusCode] = None)(s: Session)(implicit timeout: FiniteDuration): Xor[CornichonError, JsonHttpResponse] = {
    for {
      urlResolved ← resolver.fillPlaceHolder(url)(s.content)
      res ← Await.result(httpService.deleteJson(urlResolved, expected), timeout)
    } yield {
      res
    }
  }

  private def fillPlaceholderInJson(js: JsValue)(s: Session): Xor[ResolverError, JsValue] =
    resolver.fillPlaceHolder(js.toString())(s.content).map(_.parseJson)
}
