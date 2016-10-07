package com.github.agourlay.cornichon.http.server

import java.net.NetworkInterface

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ HttpRequest, HttpResponse }
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink
import com.github.agourlay.cornichon.dsl.CloseableResource

import scala.collection.JavaConverters._
import scala.concurrent.{ ExecutionContext, Future }

class AkkaHttpServer(port: Int, requestHandler: HttpRequest ⇒ Future[HttpResponse])(implicit system: ActorSystem, mat: ActorMaterializer, executionContext: ExecutionContext) extends HttpServer {

  private val interface = bestInterface()

  def startServer() = {
    Http()
      .bind(interface = interface, port)
      .to(Sink.foreach { _ handleWithAsyncHandler requestHandler })
      .run()
      .map { serverBinding ⇒
        val fullAddress = s"http://$interface:${serverBinding.localAddress.getPort}"
        val closeable = new CloseableResource {
          def stopResource() = serverBinding.unbind()
        }
        (fullAddress, closeable)
      }
  }

  private def bestInterface(): String =
    NetworkInterface.getNetworkInterfaces.asScala
      .filter(_.isUp)
      .flatMap(_.getInetAddresses.asScala)
      .find(i ⇒ i.isSiteLocalAddress)
      .map(_.getHostAddress).getOrElse("localhost")

}
