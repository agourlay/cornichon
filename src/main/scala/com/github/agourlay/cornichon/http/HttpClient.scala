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
import akka.stream.scaladsl.Source
import cats.data.Xor
import cats.data.Xor.{ left, right }
import com.github.agourlay.cornichon.core.CornichonLogger
import de.heikoseeberger.akkasse.EventStreamUnmarshalling._
import de.heikoseeberger.akkasse.ServerSentEvent
import org.json4s._
import org.json4s.native.JsonMethods._
import spray.json._
import spray.json.DefaultJsonProtocol._
import spray.json.JsValue

import scala.concurrent.duration._
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ Await, ExecutionContext, Future }

class HttpClient(implicit actorSystem: ActorSystem, mat: Materializer) extends CornichonLogger {

  implicit private val ec: ExecutionContext = actorSystem.dispatcher

  private def toSprayJson(jValue: JValue): JsValue = compact(render(jValue)).parseJson

  private def requestRunner(req: HttpRequest, headers: Seq[HttpHeader]) =
    Http()
      .singleRequest(req.withHeaders(collection.immutable.Seq(headers: _*)))
      .flatMap(toCornichonResponse)
      .recover(exceptionMapper)

  private def uriBuilder(url: String, params: Seq[(String, String)]): Uri = Uri(url).withQuery(params: _*)

  def postJson(payload: JValue, url: String, params: Seq[(String, String)], headers: Seq[HttpHeader]): Future[Xor[HttpError, CornichonHttpResponse]] =
    requestRunner(Post(uriBuilder(url, params), toSprayJson(payload)), headers)

  def putJson(payload: JValue, url: String, params: Seq[(String, String)], headers: Seq[HttpHeader]): Future[Xor[HttpError, CornichonHttpResponse]] =
    requestRunner(Put(uriBuilder(url, params), toSprayJson(payload)), headers)

  def deleteJson(url: String, params: Seq[(String, String)], headers: Seq[HttpHeader]): Future[Xor[HttpError, CornichonHttpResponse]] =
    requestRunner(Delete(uriBuilder(url, params)), headers)

  def getJson(url: String, params: Seq[(String, String)], headers: Seq[HttpHeader]): Future[Xor[HttpError, CornichonHttpResponse]] =
    requestRunner(Get(uriBuilder(url, params)), headers)

  case class CornichonSSE(data: String, eventType: Option[String] = None, id: Option[String] = None)

  object CornichonSSE {
    def build(sse: ServerSentEvent): CornichonSSE = CornichonSSE(sse.data, sse.eventType, sse.id)
    implicit val formatServerSentEvent = jsonFormat3(CornichonSSE.apply)
  }

  def getSSE(url: String, params: Seq[(String, String)], takeWithin: FiniteDuration, headers: Seq[HttpHeader]): Future[Xor[HttpError, CornichonHttpResponse]] = {
    import spray.json.DefaultJsonProtocol._
    import spray.json._

    Http().singleRequest(Get(uriBuilder(url, params)).withHeaders(collection.immutable.Seq(headers: _*)))
      .flatMap(expectSSE)
      .map { sse ⇒
        sse.map { source ⇒
          val r = source.filter(_.data.nonEmpty)
            .takeWithin(takeWithin)
            .runFold(List.empty[CornichonSSE])((acc, sse) ⇒ {
              acc :+ CornichonSSE.build(sse)
            })
            .map { events ⇒
              CornichonHttpResponse(
                status = StatusCodes.OK, //TODO get real status code?
                headers = collection.immutable.Seq.empty[HttpHeader], //TODO get real headers?
                body = events.toJson.prettyPrint
              )
            }
          Await.result(r, takeWithin + 1.second)
        }
      }
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

  private def toCornichonResponse(httpResponse: HttpResponse): Future[Xor[HttpError, CornichonHttpResponse]] =
    Unmarshal(Gzip.decode(httpResponse)).to[String].map { body: String ⇒
      right(CornichonHttpResponse.fromResponse(httpResponse, body))
    }.recover {
      case e: Exception ⇒
        left(ResponseError(e, httpResponse))
    }
}
