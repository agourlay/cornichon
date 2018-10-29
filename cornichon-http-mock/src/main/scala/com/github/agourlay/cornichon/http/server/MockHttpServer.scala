package com.github.agourlay.cornichon.http.server

import java.net.NetworkInterface

import cats.effect.ExitCode
import cats.effect.concurrent.Ref
import com.github.agourlay.cornichon.core.CornichonError
import com.github.agourlay.cornichon.dsl.CloseableResource
import fs2.concurrent.SignallingRef
import monix.eval.Task
import monix.execution.Scheduler
import org.http4s.HttpRoutes
import org.http4s.server.blaze.BlazeBuilder

import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scala.util.Random

class MockHttpServer(interface: Option[String], port: Option[Range], mockService: HttpRoutes[Task], maxRetries: Int = 5)(implicit scheduler: Scheduler) extends HttpServer {

  private val selectedInterface = interface.getOrElse(bestInterface())
  private val randomPortOrder = port.fold(0 :: Nil)(r ⇒ Random.shuffle(r.toList))

  def startServer(): Task[(String, CloseableResource)] =
    if (randomPortOrder.isEmpty)
      Task.raiseError(MockHttpServerError.toException)
    else
      startServerTryPorts(randomPortOrder)

  private def startServerTryPorts(ports: List[Int], retry: Int = 0): Task[(String, CloseableResource)] =
    startBlazeServer(ports.head).onErrorHandleWith {
      case _: java.net.BindException if ports.length > 1 ⇒
        startServerTryPorts(ports.tail, retry)
      case _: java.net.BindException if retry < maxRetries ⇒
        val sleepFor = retry + 1
        println(s"Could not start server on any port. Retrying in $sleepFor seconds...")
        startServerTryPorts(randomPortOrder, retry + 1).delayExecution((retry + 1).seconds)
    }

  private def startBlazeServer(port: Int): Task[(String, CloseableResource)] =
    SignallingRef[Task, Boolean](false).flatMap { signal ⇒

      val server = BlazeBuilder[Task]
        .bindHttp(port, selectedInterface)
        .mountService(mockService, "/")
        .serveWhile(signal, Ref.unsafe(ExitCode.Success))
        .compile
        .drain
        .uncancelable // we do not want the Racing trick below to stop the server

      val shutdownHook = {
        val fullAddress = s"http://$selectedInterface:$port"
        val closeable = new CloseableResource {
          def stopResource(): Task[Unit] = signal.set(true)
        }
        Task.now(fullAddress -> closeable)
      }

      // We need to use `serveWhile` because of the explicit lifecycle
      // this means the Tasks is not returning until the server is shutdown
      // the problem is that we need:
      // - to retry in case of error
      // - succeed ASAP
      // Having the `allocate` pattern on the server could cleanup this mess
      Task.race(server, shutdownHook.delayExecution(200.millis)) map {
        case Left(_)   ⇒ throw new IllegalStateException("The server returned with a success too quickly")
        case Right(ss) ⇒ ss
      }
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
