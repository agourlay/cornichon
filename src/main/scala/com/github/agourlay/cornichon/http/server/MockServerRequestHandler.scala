package com.github.agourlay.cornichon.http.server

import akka.pattern._
import akka.actor.{ ActorRef, ActorSystem }
import akka.http.scaladsl.client.RequestBuilding._
import akka.http.scaladsl.coding.Gzip
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorMaterializer
import com.github.agourlay.cornichon.http.{ HttpMethod ⇒ CornichonHttpMethod, HttpMethods ⇒ CornichonHttpMethods, HttpRequest ⇒ CornichonHttpRequest }
import com.github.agourlay.cornichon.util.ShowInstances._
import com.github.agourlay.cornichon.http.server.MockServerResultsHolder._

import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration._

case class MockServerRequestHandler(serverName: String, port: Int)(implicit system: ActorSystem, am: ActorMaterializer, executionContext: ExecutionContext) {

  val requestReceivedRepo = system.actorOf(MockServerResultsHolder.props(), s"MockServerResultsHolder-$serverName-$port")

  // TODO one more future to chain later
  implicit private val timeout = akka.util.Timeout(500.millis)

  def getRecordedRequests: Future[Vector[CornichonHttpRequest[String]]] =
    (requestReceivedRepo ? GetReceivedRequest)
      .mapTo[RegisteredRequests]
      .map(_.requests)

  val requestHandler: ActorRef ⇒ HttpRequest ⇒ Future[HttpResponse] = ref ⇒ {
    case r @ HttpRequest(POST, _, _, _, _) ⇒
      saveRequest(r, ref).map(_ ⇒ HttpResponse(201))

    case r: HttpRequest ⇒
      saveRequest(r, ref).map(_ ⇒ HttpResponse(200))
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

  def saveRequest(akkaReq: HttpRequest, requestRepo: ActorRef) = {
    Unmarshal(Gzip.decode(akkaReq)).to[String].flatMap { decodeBody: String ⇒
      val req = CornichonHttpRequest[String](
        method = httpMethodMapper(akkaReq.method),
        url = akkaReq.uri.path.toString(),
        body = Some(decodeBody),
        params = akkaReq.uri.query().map(p ⇒ (p._1, p._2)),
        headers = akkaReq.headers.map(h ⇒ (h.name(), h.value()))
      )
      (requestRepo ? RegisterRequest(req)).mapTo[RequestRegistered]
    }
  }

  def shutdown() = requestReceivedRepo ! ClearRegisteredRequest
}
