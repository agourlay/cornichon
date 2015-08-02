package com.github.agourlay.cornichon.http

import java.util.concurrent.TimeoutException

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ HttpHeader, HttpResponse, StatusCode }
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.http.scaladsl.client.RequestBuilding._
import akka.stream.Materializer
import cats.data.Xor
import cats.data.Xor.{ left, right }
import spray.json.{ JsValue, JsObject }
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._

import scala.collection.immutable
import scala.concurrent.{ ExecutionContext, Future }

class HttpService(implicit actorSystem: ActorSystem, materializer: Materializer) {

  implicit val ec: ExecutionContext = actorSystem.dispatcher

  def postJson(payload: JsObject, url: String, expected: Option[StatusCode], headers: immutable.Seq[HttpHeader] = immutable.Seq.empty): Future[Xor[HttpError, JsonHttpResponse]] = {
    Http()
      .singleRequest(Post(url, payload).withHeaders(headers))
      .flatMap(expectJson(expected))
      .recover(exceptionMapper)
  }

  def putJson(payload: JsObject, url: String, expected: Option[StatusCode], headers: immutable.Seq[HttpHeader] = immutable.Seq.empty): Future[Xor[HttpError, JsonHttpResponse]] = {
    Http()
      .singleRequest(Put(url, payload).withHeaders(headers))
      .flatMap(expectJson(expected))
      .recover(exceptionMapper)
  }

  def getJson(url: String, expected: Option[StatusCode], headers: immutable.Seq[HttpHeader] = immutable.Seq.empty): Future[Xor[HttpError, JsonHttpResponse]] = {
    Http()
      .singleRequest(Get(url).withHeaders(headers))
      .flatMap(expectJson(expected))
      .recover(exceptionMapper)
  }

  def deleteJson(url: String, expected: Option[StatusCode], headers: immutable.Seq[HttpHeader] = immutable.Seq.empty): Future[Xor[HttpError, JsonHttpResponse]] = {
    Http()
      .singleRequest(Delete(url).withHeaders(headers))
      .flatMap(expectJson(expected))
      .recover(exceptionMapper)
  }

  def exceptionMapper: PartialFunction[Throwable, Xor[HttpError, JsonHttpResponse]] = {
    case e: TimeoutException ⇒ left(TimeoutError(e.getMessage))
  }

  def expectJson(statusCode: Option[StatusCode])(httpResponse: HttpResponse): Future[Xor[HttpError, JsonHttpResponse]] =
    Unmarshal(httpResponse).to[JsValue].map { body: JsValue ⇒
      statusCode.fold[Xor[HttpError, JsonHttpResponse]](right(JsonHttpResponse.fromResponse(httpResponse, body))) { s ⇒
        if (s == httpResponse.status)
          right(JsonHttpResponse.fromResponse(httpResponse, body))
        else
          left(StatusError(s, httpResponse.status, body))
      }
    }.recover {
      case e: Exception ⇒ left(JsonError(e))
    }
}
