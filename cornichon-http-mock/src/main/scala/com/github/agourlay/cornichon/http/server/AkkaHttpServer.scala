package com.github.agourlay.cornichon.http.server

import java.net.NetworkInterface

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ HttpRequest, HttpResponse }
import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import com.github.agourlay.cornichon.dsl.CloseableResource

import scala.collection.JavaConverters._
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Random

class AkkaHttpServer(interface: Option[String], port: Option[Range], requestHandler: HttpRequest ⇒ Future[HttpResponse])(implicit system: ActorSystem, mat: Materializer, executionContext: ExecutionContext) extends HttpServer {

  private val selectedInterface = interface.getOrElse(bestInterface())
  // TODO handle case of random port from range taken, retry?
  private val selectedPort = port.fold(0)(r ⇒ Random.shuffle(r.toList).head)

  def startServer() = {
    Http()
      .bind(interface = selectedInterface, selectedPort)
      .to(Sink.foreach { _ handleWithAsyncHandler requestHandler })
      .run()
      .map { serverBinding ⇒
        val fullAddress = s"http://$selectedInterface:${serverBinding.localAddress.getPort}"
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
