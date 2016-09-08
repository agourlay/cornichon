package com.github.agourlay.cornichon.http.server

import akka.http.scaladsl.client.RequestBuilding._
import akka.http.scaladsl.coding.Gzip
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorMaterializer
import com.github.agourlay.cornichon.http.{ HttpMethod ⇒ CornichonHttpMethod, HttpMethods ⇒ CornichonHttpMethods, HttpRequest ⇒ CornichonHttpRequest }
import com.github.agourlay.cornichon.util.ShowInstances._

import scala.concurrent.ExecutionContext

class MockRequestHandler(implicit system: ActorMaterializer, executionContext: ExecutionContext) {

  private val requests = new scala.collection.mutable.ArrayBuffer[CornichonHttpRequest[String]]

  def getRecordedRequests = requests.toVector

  val requestHandler: HttpRequest ⇒ HttpResponse = {
    case r @ HttpRequest(POST, _, _, _, _) ⇒
      saveRequest(r)
      HttpResponse(201)

    case r: HttpRequest ⇒
      saveRequest(r)
      HttpResponse(200)
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

  //FIXME Don't look too close here...
  def saveRequest(akkaReq: HttpRequest) = {
    Unmarshal(Gzip.decode(akkaReq)).to[String].map { decodeBody: String ⇒
      val req = CornichonHttpRequest[String](
        method = httpMethodMapper(akkaReq.method),
        url = akkaReq.uri.path.toString(),
        body = Some(decodeBody),
        params = Seq.empty,
        headers = Seq.empty
      )
      synchronized {
        requests.+=(req)
      }
    }
  }
}
