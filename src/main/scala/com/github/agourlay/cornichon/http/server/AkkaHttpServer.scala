package com.github.agourlay.cornichon.http.server

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ HttpRequest, HttpResponse }
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink

import scala.concurrent.{ ExecutionContext, Future }

class AkkaHttpServer(port: Int, requestHandler: HttpRequest ⇒ HttpResponse)(implicit system: ActorSystem, mat: ActorMaterializer, executionContext: ExecutionContext) extends HttpServer {

  // FIXME var :(
  var bindingFuture: Future[Http.ServerBinding] = _

  def startServer = {
    val serverSource = Http().bind(interface = "localhost", port)
    bindingFuture = serverSource.to(Sink.foreach { _ handleWithSyncHandler requestHandler }).run()
    bindingFuture.map(_ ⇒ ())
  }

  def stopServer = bindingFuture.flatMap(_.unbind())
}
