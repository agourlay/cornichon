package com.github.agourlay.cornichon.http.client

import java.util.concurrent.TimeoutException

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.client.RequestBuilding._
import akka.http.scaladsl.coding.Gzip
import akka.http.scaladsl.marshalling._
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import cats.data.Xor
import cats.data.Xor.{ left, right }
import com.github.agourlay.cornichon.core.CornichonLogger
import com.github.agourlay.cornichon.http._
import de.heikoseeberger.akkasse.EventStreamUnmarshalling._
import de.heikoseeberger.akkasse.ServerSentEvent
import org.json4s._
import org.json4s.jackson.JsonMethods._

import scala.concurrent.duration.{ FiniteDuration, _ }
import scala.concurrent.{ Await, ExecutionContext, Future }

class AkkaHttpClient(implicit system: ActorSystem, mat: Materializer) extends HttpClient with CornichonLogger {
  implicit private val ec: ExecutionContext = system.dispatcher

  implicit private val formats = DefaultFormats

  private def requestRunner(req: HttpRequest, headers: Seq[HttpHeader]) =
    Http()
      .singleRequest(req.withHeaders(collection.immutable.Seq(headers: _*)))
      .flatMap(toCornichonResponse)
      .recover(exceptionMapper)

  implicit def JValueMarshaller: ToEntityMarshaller[JValue] =
    Marshaller.StringMarshaller.wrap(MediaTypes.`application/json`)(j ⇒ compact(render(j)))

  private def uriBuilder(url: String, params: Seq[(String, String)]): Uri = Uri(url).withQuery(Query(params: _*))

  def postJson(payload: JValue, url: String, params: Seq[(String, String)], headers: Seq[HttpHeader]): Future[Xor[HttpError, CornichonHttpResponse]] =
    requestRunner(Post(uriBuilder(url, params), payload), headers)

  def putJson(payload: JValue, url: String, params: Seq[(String, String)], headers: Seq[HttpHeader]): Future[Xor[HttpError, CornichonHttpResponse]] =
    requestRunner(Put(uriBuilder(url, params), payload), headers)

  def deleteJson(url: String, params: Seq[(String, String)], headers: Seq[HttpHeader]): Future[Xor[HttpError, CornichonHttpResponse]] =
    requestRunner(Delete(uriBuilder(url, params)), headers)

  def getJson(url: String, params: Seq[(String, String)], headers: Seq[HttpHeader]): Future[Xor[HttpError, CornichonHttpResponse]] =
    requestRunner(Get(uriBuilder(url, params)), headers)

  def getSSE(url: String, params: Seq[(String, String)], takeWithin: FiniteDuration, headers: Seq[HttpHeader]): Future[Xor[HttpError, CornichonHttpResponse]] = {
    Http()
      .singleRequest(Get(uriBuilder(url, params)).withHeaders(collection.immutable.Seq(headers: _*)))
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
          Await.result(r, takeWithin + 1.second)
        }
      }
  }

  // TODO https://github.com/akka/akka/issues/17275
  def getWS(url: String, params: Seq[(String, String)], takeWithin: FiniteDuration, headers: Seq[HttpHeader]): Future[Xor[HttpError, CornichonHttpResponse]] = ???

  private def exceptionMapper: PartialFunction[Throwable, Xor[HttpError, CornichonHttpResponse]] = {
    case e: TimeoutException ⇒ left(TimeoutError(e.getMessage))
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

  def shutdown() = {
    Http().shutdownAllConnectionPools()
  }
}