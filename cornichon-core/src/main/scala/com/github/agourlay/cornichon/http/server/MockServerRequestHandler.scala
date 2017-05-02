package com.github.agourlay.cornichon.http.server

import java.util.UUID

import akka.pattern._
import akka.actor.ActorSystem
import akka.http.scaladsl.client.RequestBuilding._
import akka.http.scaladsl.coding.Gzip
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model.{ HttpRequest, _ }
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.Materializer
import cats.instances.string._
import com.github.agourlay.cornichon.core.Done
import com.github.agourlay.cornichon.http.{ HttpMethod ⇒ CornichonHttpMethod, HttpMethods ⇒ CornichonHttpMethods, HttpRequest ⇒ CornichonHttpRequest }
import com.github.agourlay.cornichon.http.server.MockServerResultsHolder._
import com.github.agourlay.cornichon.json.CornichonJson
import io.circe.Json

import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration._

case class MockServerRequestHandler(serverName: String)(implicit system: ActorSystem, mat: Materializer, executionContext: ExecutionContext) {

  val requestReceivedRepo = system.actorOf(MockServerResultsHolder.props(), s"MockServerResultsHolder-$serverName-${UUID.randomUUID()}")

  implicit private val timeout = akka.util.Timeout(500.millis)

  def fetchRecordedRequests(): Future[Vector[CornichonHttpRequest[String]]] =
    (requestReceivedRepo ? GetReceivedRequest)
      .mapTo[RegisteredRequests]
      .map(_.requests)

  def resetRecordedRequests(): Future[Done] =
    (requestReceivedRepo ? ClearRegisteredRequest).mapTo[Done]

  def toggleErrorMode(): Future[Done] =
    (requestReceivedRepo ? ToggleErrorMode).mapTo[Done]

  def fetchErrorMode(): Future[Boolean] =
    (requestReceivedRepo ? GetErrorMode).mapTo[ErrorMode].map(_.errorMode)

  def fetchRecordedRequestsAsJson() = fetchRecordedRequests().map { requests ⇒
    requests.map { req ⇒
      Json.fromFields(
        Seq(
          "body" → CornichonJson.parseJsonUnsafe(req.body.getOrElse("")),
          "url" → Json.fromString(req.url),
          "method" → Json.fromString(req.method.name),
          "parameters" → Json.fromFields(req.params.map { case (n, v) ⇒ (n, Json.fromString(v)) }),
          "headers" → Json.fromFields(req.headers.map { case (n, v) ⇒ (n, Json.fromString(v)) })
        )
      )
    }
  }

  def withMockAdministration(wrapped: HttpRequest ⇒ HttpResponse)(r: HttpRequest): Future[HttpResponse] = r match {
    case HttpRequest(GET, Uri.Path("/requests-received"), _, _, _) ⇒
      fetchRecordedRequestsAsJson().map { reqs ⇒
        val body = Json.fromValues(reqs)
        val entity = HttpEntity(ContentTypes.`application/json`, body.spaces2)
        HttpResponse(200).withEntity(entity)
      }

    case HttpRequest(GET, Uri.Path("/reset"), _, _, _) ⇒
      resetRecordedRequests().map { _ ⇒
        HttpResponse(200)
      }

    case HttpRequest(POST, Uri.Path("/toggle-error-mode"), _, _, _) ⇒
      toggleErrorMode().map { _ ⇒
        HttpResponse(200)
      }

    case _ ⇒ fetchErrorMode().flatMap { e ⇒
      if (e) Future.successful(HttpResponse(500))
      else saveRequest(r).map(_ ⇒ wrapped(r))
    }
  }

  val defaultMockRoute: HttpRequest ⇒ HttpResponse = {
    case HttpRequest(POST, _, _, _, _) ⇒ HttpResponse(201)
    case _: HttpRequest                ⇒ HttpResponse(200)
  }

  // The nested route definition could be provided by the client in the future.
  val requestHandler: HttpRequest ⇒ Future[HttpResponse] = withMockAdministration(defaultMockRoute)

  def httpMethodMapper(method: HttpMethod): CornichonHttpMethod = method match {
    case Delete.method  ⇒ CornichonHttpMethods.DELETE
    case Get.method     ⇒ CornichonHttpMethods.GET
    case Head.method    ⇒ CornichonHttpMethods.HEAD
    case Options.method ⇒ CornichonHttpMethods.OPTIONS
    case Patch.method   ⇒ CornichonHttpMethods.PATCH
    case Post.method    ⇒ CornichonHttpMethods.POST
    case Put.method     ⇒ CornichonHttpMethods.PUT
  }

  def saveRequest(akkaReq: HttpRequest) = {
    Unmarshal(Gzip.decode(akkaReq)).to[String].flatMap { decodedBody: String ⇒
      val req = CornichonHttpRequest[String](
        method = httpMethodMapper(akkaReq.method),
        url = akkaReq.uri.path.toString(),
        body = Some(decodedBody),
        params = akkaReq.uri.query().map(p ⇒ (p._1, p._2)),
        headers = akkaReq.headers.map(h ⇒ (h.name(), h.value()))
      )
      (requestReceivedRepo ? RegisterRequest(req)).mapTo[RequestRegistered]
    }
  }

  def shutdown() = requestReceivedRepo ! ClearRegisteredRequest
}
