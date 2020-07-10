package com.github.agourlay.cornichon.http.server

import java.net.NetworkInterface

import com.github.agourlay.cornichon.core.CornichonError
import monix.eval.Task
import org.http4s.HttpRoutes
import org.http4s.server.Router
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.implicits._

import scala.jdk.CollectionConverters._
import scala.concurrent.duration._
import scala.util.Random

class MockHttpServer[A](interface: Option[String], port: Option[Range], mockService: HttpRoutes[Task], maxRetries: Int = 5)(useFromAddress: String => Task[A]) {

  private val selectedInterface = interface.getOrElse(bestInterface())
  private val randomPortOrder = port.fold(0 :: Nil)(r => Random.shuffle(r.toList))

  private val mockRouter = Router("/" -> mockService).orNotFound

  def useServer(): Task[A] =
    if (randomPortOrder.isEmpty)
      Task.raiseError(MockHttpServerError.toException)
    else
      startServerTryPorts(randomPortOrder)

  private def startServerTryPorts(ports: List[Int], retry: Int = 0): Task[A] =
    startBlazeServer(ports.head).onErrorRecoverWith {
      case _: java.net.BindException if ports.length > 1 =>
        startServerTryPorts(ports.tail, retry)
      case _: java.net.BindException if retry < maxRetries =>
        val sleepFor = retry + 1
        println(s"Could not start server on any port. Retrying in $sleepFor seconds...")
        startServerTryPorts(randomPortOrder, retry = retry + 1).delayExecution(sleepFor.seconds)
    }

  private def startBlazeServer(port: Int): Task[A] =
    EmberServerBuilder.default[Task]
      .withPort(port)
      .withHost(selectedInterface)
      .withHttpApp(mockRouter)
      .build
      .use(server => useFromAddress(s"http://${server.address.getHostString}:${server.address.getPort}"))

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
