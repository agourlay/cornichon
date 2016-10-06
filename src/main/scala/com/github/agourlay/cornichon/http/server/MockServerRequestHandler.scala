package com.github.agourlay.cornichon.http.server

import java.util.UUID

import akka.pattern._
import akka.actor.ActorSystem
import akka.http.scaladsl.client.RequestBuilding._
import akka.http.scaladsl.coding.Gzip
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorMaterializer
import com.github.agourlay.cornichon.http.{ HttpMethod ⇒ CornichonHttpMethod, HttpMethods ⇒ CornichonHttpMethods, HttpRequest ⇒ CornichonHttpRequest }
import com.github.agourlay.cornichon.util.ShowInstances._
import com.github.agourlay.cornichon.http.server.MockServerResultsHolder._
import com.github.agourlay.cornichon.json.CornichonJson
import io.circe.Json

import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration._

case class MockServerRequestHandler(serverName: String, port: Int)(implicit system: ActorSystem, am: ActorMaterializer, executionContext: ExecutionContext) {

  val requestReceivedRepo = system.actorOf(MockServerResultsHolder.props(), s"MockServerResultsHolder-$serverName-${UUID.randomUUID()}")

  // TODO one more future to chain later
  implicit private val timeout = akka.util.Timeout(500.millis)

  def fetchRecordedRequests(): Future[Vector[CornichonHttpRequest[String]]] =
    (requestReceivedRepo ? GetReceivedRequest)
      .mapTo[RegisteredRequests]
      .map(_.requests)

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

  val requestHandler: HttpRequest ⇒ Future[HttpResponse] = {
    case HttpRequest(GET, Uri.Path("/requests-received"), _, _, _) ⇒
      fetchRecordedRequestsAsJson().map { reqs ⇒
        val body = Json.fromValues(reqs)
        val entity = HttpEntity(ContentTypes.`application/json`, body.spaces2)
        HttpResponse(200).withEntity(entity)
      }

    case r @ HttpRequest(POST, _, _, _, _) ⇒
      saveRequest(r).map(_ ⇒ HttpResponse(201))

    case r: HttpRequest ⇒
      saveRequest(r).map(_ ⇒ HttpResponse(200))

  }

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
