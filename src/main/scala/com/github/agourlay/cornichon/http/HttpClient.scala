package com.github.agourlay.cornichon.http

import java.util.concurrent.TimeoutException

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.client.RequestBuilding._
import akka.http.scaladsl.coding.Gzip
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.Materializer
import akka.stream.scaladsl.{ FlattenStrategy, Source }
import cats.data.Xor
import cats.data.Xor.{ left, right }
import com.github.agourlay.cornichon.core.CornichonLogger
import de.heikoseeberger.akkasse.EventStreamUnmarshalling._
import de.heikoseeberger.akkasse.ServerSentEvent
import spray.json.JsValue

import scala.Console._
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ ExecutionContext, Future }

class HttpClient(implicit actorSystem: ActorSystem, mat: Materializer) extends CornichonLogger {

  implicit private val ec: ExecutionContext = actorSystem.dispatcher

  private def requestRunner(req: HttpRequest, headers: Seq[HttpHeader]) =
    Http()
      .singleRequest(req.withHeaders(collection.immutable.Seq(headers: _*)))
      .flatMap(expectJson)
      .recover(exceptionMapper)

  def postJson(payload: JsValue, url: String, params: Seq[(String, String)], headers: Seq[HttpHeader]): Future[Xor[HttpError, CornichonHttpResponse]] =
    requestRunner(Post(Uri(url).withQuery(params: _*), payload), headers)

  def putJson(payload: JsValue, url: String, params: Seq[(String, String)], headers: Seq[HttpHeader]): Future[Xor[HttpError, CornichonHttpResponse]] =
    requestRunner(Put(Uri(url).withQuery(params: _*), payload), headers)

  def deleteJson(url: String, params: Seq[(String, String)], headers: Seq[HttpHeader]): Future[Xor[HttpError, CornichonHttpResponse]] =
    requestRunner(Delete(Uri(url).withQuery(params: _*)), headers)

  def getJson(url: String, params: Seq[(String, String)], headers: Seq[HttpHeader]): Future[Xor[HttpError, CornichonHttpResponse]] =
    requestRunner(Get(Uri(url).withQuery(params: _*)), headers)

  def getSSE(url: String, params: Seq[(String, String)], takeWithin: FiniteDuration, headers: Seq[HttpHeader]) = {
    val request = Get(Uri(url).withQuery(params: _*)).withHeaders(collection.immutable.Seq(headers: _*))
    val host = request.uri.authority.host.toString()
    val port = request.uri.effectivePort

    Source.single(request)
      .via(Http().outgoingConnection(host, port))
      .mapAsync(1)(expectSSE)
      .map { sse ⇒
        sse.fold(e ⇒ {
          logger.error(RED + s"SSE connection error $e" + RESET)
          Source.empty[ServerSentEvent]
        }, s ⇒ s)
      }
      .flatten(FlattenStrategy.concat[ServerSentEvent])
      .filter(_.data.nonEmpty)
      .takeWithin(takeWithin).runFold(List.empty[ServerSentEvent])((acc, sse) ⇒ {
        acc :+ sse
      })
  }

  private def exceptionMapper: PartialFunction[Throwable, Xor[HttpError, CornichonHttpResponse]] = {
    case e: TimeoutException ⇒ left(TimeoutError(e.getMessage))
  }

  private def expectSSE(httpResponse: HttpResponse): Future[Xor[HttpError, Source[ServerSentEvent, Any]]] =
    Unmarshal(Gzip.decode(httpResponse)).to[Source[ServerSentEvent, Any]].map { sse ⇒
      right(sse)
    }.recover {
      case e: Exception ⇒ left(SseError(e))
    }

  private def expectJson(httpResponse: HttpResponse): Future[Xor[HttpError, CornichonHttpResponse]] =
    Unmarshal(Gzip.decode(httpResponse)).to[String].map { body: String ⇒
      right(CornichonHttpResponse.fromResponse(httpResponse, body))
    }.recover {
      case e: Exception ⇒
        left(ResponseError(e, httpResponse))
    }
}
