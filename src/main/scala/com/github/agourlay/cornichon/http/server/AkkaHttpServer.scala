package com.github.agourlay.cornichon.http.server

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ HttpRequest, HttpResponse }
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink
import com.github.agourlay.cornichon.dsl.CloseableResource

import scala.concurrent.{ ExecutionContext, Future }

class AkkaHttpServer(
    port: Int,
    requestHandler: HttpRequest ⇒ Future[HttpResponse]
)(implicit
  system: ActorSystem,
    mat: ActorMaterializer,
    executionContext: ExecutionContext) extends HttpServer {

  def startServer() = {
    Http()
      .bind(interface = "localhost", port)
      .to(Sink.foreach { _ handleWithAsyncHandler requestHandler })
      .run()
      .map { serverBinding ⇒
        new CloseableResource {
          def stopResource() = serverBinding.unbind()
        }
      }
  }
}
