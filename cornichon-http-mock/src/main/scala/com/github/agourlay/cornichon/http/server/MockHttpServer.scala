package com.github.agourlay.cornichon.http.server

import java.net.NetworkInterface

import com.github.agourlay.cornichon.dsl.CloseableResource
import monix.eval.Task
import org.http4s.HttpService
import org.http4s.server.blaze.BlazeBuilder

import scala.collection.JavaConverters._
import scala.util.Random

class MockHttpServer(interface: Option[String], port: Option[Range], mockService: HttpService) extends HttpServer {

  private val selectedInterface = interface.getOrElse(bestInterface())
  // TODO handle case of random port from range taken, retry?
  private val selectedPort = port.fold(0)(r ⇒ Random.shuffle(r.toList).head)

  def startServer() =
    BlazeBuilder
      .bindHttp(selectedPort, selectedInterface)
      .mountService(mockService, "/")
      .start
      .map { serverBinding ⇒
        val fullAddress = s"http://$selectedInterface:${serverBinding.address.getPort}"
        val closeable = new CloseableResource {
          def stopResource() = Task.fromFuture(serverBinding.shutdown.unsafeRunAsyncFuture())
        }
        (fullAddress, closeable)
      }.unsafeRunAsyncFuture()

  private def bestInterface(): String =
    NetworkInterface.getNetworkInterfaces.asScala
      .filter(_.isUp)
      .flatMap(_.getInetAddresses.asScala)
      .find(i ⇒ i.isSiteLocalAddress)
      .map(_.getHostAddress).getOrElse("localhost")

}
