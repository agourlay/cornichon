package com.github.agourlay.cornichon.http.server

import java.net.NetworkInterface
import cats.effect.IO
import com.comcast.ip4s.{ Host, Port }
import com.github.agourlay.cornichon.core.CornichonError
import org.http4s.HttpRoutes
import org.http4s.server.Router
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.implicits._
import org.http4s.server.middleware.ResponseTiming
import scala.jdk.CollectionConverters._
import scala.concurrent.duration._
import scala.util.Random

class MockHttpServer[A](
    label: String,
    interface: Option[String],
    port: Option[Range],
    mockService: HttpRoutes[IO],
    maxPortBindingRetries: Int)(useFromAddress: String => IO[A]) {

  private val selectedInterface = interface.getOrElse(bestInterface())
  private val randomPortOrder = port.fold(0 :: Nil)(r => Random.shuffle(r.toList))

  private val mockRouter = Router("/" -> mockService).orNotFound

  def useServer(): IO[A] =
    if (randomPortOrder.isEmpty)
      IO.raiseError(MockHttpServerError.toException)
    else
      startServerTryPorts(randomPortOrder)

  private def startServerTryPorts(ports: List[Int], retry: Int = 0): IO[A] =
    startServer(ports.head).handleErrorWith {
      case _: java.net.BindException if ports.length > 1 =>
        startServerTryPorts(ports.tail, retry)
      case _: java.net.BindException if retry < maxPortBindingRetries =>
        val sleepFor = retry + 1
        println(s"Could not start server `$label` on any of the provided port(s) on interface $selectedInterface. Retrying in $sleepFor seconds...")
        startServerTryPorts(randomPortOrder, retry = retry + 1).delayBy(sleepFor.seconds)
      case e: java.net.BindException if retry == maxPortBindingRetries =>
        IO.raiseError(MockHttpServerStartError(e, label, maxPortBindingRetries, selectedInterface).toException)
    }

  private def startServer(port: Int): IO[A] =
    Port.fromInt(port) match {
      case None => IO.raiseError(new IllegalArgumentException(s"Invalid port number $port"))
      case Some(p) =>
        EmberServerBuilder.default[IO]
          .withPort(p)
          .withHost(Host.fromString(selectedInterface).get) // fixme
          .withHttpApp(ResponseTiming(mockRouter))
          .withShutdownTimeout(0.seconds) // disable graceful shutdown
          .build
          .use(server => useFromAddress(s"http://${server.address.getHostString}:${server.address.getPort}"))
    }

  private def bestInterface(): String =
    NetworkInterface.getNetworkInterfaces.asScala
      .filter(_.isUp)
      .flatMap(_.getInetAddresses.asScala)
      .find(_.isSiteLocalAddress)
      .map(_.getHostAddress)
      .getOrElse("localhost")
}

case object MockHttpServerError extends CornichonError {
  val baseErrorMessage = "the range of ports provided for the HTTP mock is invalid"
}

case class MockHttpServerStartError(e: Exception, label: String, maxPortBindingRetries: Int, interface: String) extends CornichonError {
  val baseErrorMessage = s"HTTP server mock `$label` did not start properly after $maxPortBindingRetries port binding delayed retries on interface $interface"
  override val causedBy: List[CornichonError] = CornichonError.fromThrowable(e) :: Nil
}
