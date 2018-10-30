package com.github.agourlay.cornichon.http.server

import java.net.NetworkInterface

import com.github.agourlay.cornichon.core.CornichonError

import monix.eval.Task
import monix.execution.Scheduler
import org.http4s.HttpRoutes
import org.http4s.server.blaze.BlazeBuilder

import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scala.util.Random

class MockHttpServer[A](interface: Option[String], port: Option[Range], mockService: HttpRoutes[Task], maxRetries: Int = 5)(useFromAddress: String ⇒ Task[A])(implicit scheduler: Scheduler) {

  private val selectedInterface = interface.getOrElse(bestInterface())
  private val randomPortOrder = port.fold(0 :: Nil)(r ⇒ Random.shuffle(r.toList))

  def useServer(): Task[A] =
    if (randomPortOrder.isEmpty)
      Task.raiseError(MockHttpServerError.toException)
    else
      startServerTryPorts(randomPortOrder)

  private def startServerTryPorts(ports: List[Int], retry: Int = 0): Task[A] =
    startBlazeServer(ports.head).onErrorHandleWith {
      case _: java.net.BindException if ports.length > 1 ⇒
        startServerTryPorts(ports.tail, retry)
      case _: java.net.BindException if retry < maxRetries ⇒
        val sleepFor = retry + 1
        println(s"Could not start server on any port. Retrying in $sleepFor seconds...")
        startServerTryPorts(randomPortOrder, retry + 1).delayExecution((retry + 1).seconds)
    }

  private def startBlazeServer(port: Int): Task[A] =
    BlazeBuilder[Task]
      .bindHttp(port, selectedInterface)
      .mountService(mockService, "/")
      .resource
      .use(server ⇒ useFromAddress(s"http://${server.address.getHostString}:${server.address.getPort}"))

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
