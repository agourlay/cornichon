package com.github.agourlay.cornichon.http.client

import java.util.concurrent.TimeoutException

import akka.Done
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.client.RequestBuilding._
import akka.http.scaladsl.coding.Gzip
import akka.http.scaladsl.marshalling._
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.ws.{ TextMessage, Message, WebSocketRequest }
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.Materializer
import akka.stream.scaladsl.{ Flow, Sink, Keep, Source }
import cats.data.Xor
import cats.data.Xor.{ left, right }
import com.github.agourlay.cornichon.core.CornichonLogger
import com.github.agourlay.cornichon.http._
import de.heikoseeberger.akkasse.EventStreamUnmarshalling._
import de.heikoseeberger.akkasse.ServerSentEvent
import org.json4s._
import org.json4s.jackson.JsonMethods._

import scala.collection.mutable.ListBuffer
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ Await, ExecutionContext, Future }
import scala.util.{ Failure, Success, Try }

class AkkaHttpClient(implicit system: ActorSystem, mat: Materializer) extends HttpClient with CornichonLogger {
  implicit private val ec: ExecutionContext = system.dispatcher

  implicit private val formats = DefaultFormats

  private def requestRunner(req: HttpRequest, headers: Seq[HttpHeader], timeout: FiniteDuration): Xor[HttpError, CornichonHttpResponse] = {
    val request = req.withHeaders(collection.immutable.Seq(headers: _*))
    val f = Http().singleRequest(request).flatMap(toCornichonResponse)
    waitForRequestFuture(request, f, timeout)
  }

  private def waitForRequestFuture(initialRequest: HttpRequest, f: Future[Xor[HttpError, CornichonHttpResponse]], t: FiniteDuration): Xor[HttpError, CornichonHttpResponse] =
    Try { Await.result(f, t) } match {
      case Success(s) ⇒ s
      case Failure(failure) ⇒ failure match {
        case e: TimeoutException ⇒ left(TimeoutError(e.getMessage))
        case t: Throwable        ⇒ left(RequestError(t, initialRequest.getUri().toString))
      }
    }

  implicit def JValueMarshaller: ToEntityMarshaller[JValue] =
    Marshaller.StringMarshaller.wrap(MediaTypes.`application/json`)(j ⇒ compact(render(j)))

  private def uriBuilder(url: String, params: Seq[(String, String)]): Uri = Uri(url).withQuery(Query(params: _*))

  def postJson(payload: JValue, url: String, params: Seq[(String, String)], headers: Seq[HttpHeader], timeout: FiniteDuration) =
    requestRunner(Post(uriBuilder(url, params), payload), headers, timeout)

  def putJson(payload: JValue, url: String, params: Seq[(String, String)], headers: Seq[HttpHeader], timeout: FiniteDuration) =
    requestRunner(Put(uriBuilder(url, params), payload), headers, timeout)

  def deleteJson(url: String, params: Seq[(String, String)], headers: Seq[HttpHeader], timeout: FiniteDuration) =
    requestRunner(Delete(uriBuilder(url, params)), headers, timeout)

  def getJson(url: String, params: Seq[(String, String)], headers: Seq[HttpHeader], timeout: FiniteDuration) =
    requestRunner(Get(uriBuilder(url, params)), headers, timeout)

  def getSSE(url: String, params: Seq[(String, String)], headers: Seq[HttpHeader], takeWithin: FiniteDuration) = {
    val request = Get(uriBuilder(url, params)).withHeaders(collection.immutable.Seq(headers: _*))
    val f = Http().singleRequest(request)
      .flatMap(expectSSE)
      .map { sse ⇒
        sse.map { source ⇒
          val r = source
            .takeWithin(takeWithin)
            .filter(_.data.nonEmpty)
            .runFold(Vector.empty[ServerSentEvent])(_ :+ _)
            .map { events ⇒
              CornichonHttpResponse(
                status = StatusCodes.OK, //TODO get real status code?
                headers = collection.immutable.Seq.empty[HttpHeader], //TODO get real headers?
                body = compact(render(JArray(events.map(Extraction.decompose(_)).toList)))
              )
            }
          Await.result(r, takeWithin)
        }
      }
    waitForRequestFuture(request, f, takeWithin)
  }

  // FIXME barbaric implementation
  def getWS(url: String, params: Seq[(String, String)], headers: Seq[HttpHeader], takeWithin: FiniteDuration): Xor[HttpError, CornichonHttpResponse] = {
    val uri = uriBuilder(url, params)
    val req = WebSocketRequest(uri).copy(extraHeaders = collection.immutable.Seq(headers: _*))

    val received = ListBuffer.empty[String]

    val incoming: Sink[Message, Future[Done]] =
      Sink.foreach {
        case message: TextMessage.Strict ⇒
          received += message.text
        case _ ⇒ ()
      }

    val flow = Flow.fromSinkAndSourceMat(incoming, Source.empty[Message])(Keep.left)

    val (upgradeResponse, closed) = Http().singleWebSocketRequest(req, flow)

    val responses = upgradeResponse.map { upgrade ⇒
      if (upgrade.response.status == StatusCodes.OK) right(StatusCodes.OK)
      else left(WsUpgradeError(upgrade.response.status.intValue()))
    }

    Thread.sleep(takeWithin.toMillis)
    responses.value.fold(throw TimeoutError("Websocket connection did not complete in time")) {
      case Failure(e) ⇒ throw e
      case Success(s) ⇒ s.map{_ =>
        CornichonHttpResponse(
          status = StatusCodes.OK,
          headers = collection.immutable.Seq.empty[HttpHeader],
          body = compact(render(JArray(received.map(Extraction.decompose(_)).toList)))
        )
      }
    }
  }

  private def expectSSE(httpResponse: HttpResponse): Future[Xor[HttpError, Source[ServerSentEvent, Any]]] =
    Unmarshal(Gzip.decode(httpResponse)).to[Source[ServerSentEvent, Any]].map { sse ⇒
      right(sse)
    }.recover {
      case e: Exception ⇒ left(SseError(e))
    }

  private def toCornichonResponse(httpResponse: HttpResponse): Future[Xor[HttpError, CornichonHttpResponse]] =
    Unmarshal(Gzip.decode(httpResponse)).to[String].map { body: String ⇒
      right(CornichonHttpResponse.fromResponse(httpResponse, body))
    }.recover {
      case e: Exception ⇒
        left(ResponseError(e, httpResponse))
    }

  def shutdown() = Http().shutdownAllConnectionPools()
}