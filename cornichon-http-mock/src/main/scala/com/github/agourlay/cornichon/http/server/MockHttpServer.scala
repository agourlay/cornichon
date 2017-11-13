package com.github.agourlay.cornichon.http.server

import java.net.NetworkInterface

import com.github.agourlay.cornichon.core.CornichonError
import com.github.agourlay.cornichon.dsl.CloseableResource
import fs2.{ Scheduler, Strategy }
import monix.eval.Task
import org.http4s.HttpService
import org.http4s.server.blaze.BlazeBuilder

import scala.collection.JavaConverters._
import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration._
import scala.util.Random

class MockHttpServer(interface: Option[String], port: Option[Range], mockService: HttpService, maxRetries: Int = 5) extends HttpServer {

  implicit val strategy = Strategy.fromExecutionContext(ExecutionContext.Implicits.global)
  implicit val scheduler = Scheduler.fromFixedDaemonPool(1)

  private val selectedInterface = interface.getOrElse(bestInterface())
  private val randomPortOrder = port.fold(List(0))(r ⇒ Random.shuffle(r.toList))

  def startServer(): Future[(String, CloseableResource)] =
    if (randomPortOrder.isEmpty)
      Future.failed(MockHttpServerError.toException)
    else
      startServerTryPorts(randomPortOrder).unsafeRunAsyncFuture()

  private def startServerTryPorts(ports: List[Int], retry: Int = 0): fs2.Task[(String, CloseableResource)] =
    startBlazeServer(ports.head).handleWith {
      case _: java.net.BindException if ports.length > 1 ⇒
        startServerTryPorts(ports.tail, retry)
      case _: java.net.BindException if retry < maxRetries ⇒
        val sleepFor = retry + 1
        println(s"Could not start server on any port. Retrying in $sleepFor seconds...")
        startServerTryPorts(randomPortOrder, retry + 1).schedule((retry + 1) seconds)
    }

  private def startBlazeServer(port: Int): fs2.Task[(String, CloseableResource)] =
    BlazeBuilder
      .bindHttp(port, selectedInterface)
      .mountService(mockService, "/")
      .start
      .map { serverBinding ⇒
        val fullAddress = s"http://$selectedInterface:${serverBinding.address.getPort}"
        val closeable = new CloseableResource {
          def stopResource() = Task.fromFuture(serverBinding.shutdown.unsafeRunAsyncFuture())
        }
        (fullAddress, closeable)
      }

  private def bestInterface(): String =
    NetworkInterface.getNetworkInterfaces.asScala
      .filter(_.isUp)
      .flatMap(_.getInetAddresses.asScala)
      .find(i ⇒ i.isSiteLocalAddress)
      .map(_.getHostAddress).getOrElse("localhost")

}

case object MockHttpServerError extends CornichonError {
  def baseErrorMessage = "the range of ports provided for the HTTP mock is invalid"
}
