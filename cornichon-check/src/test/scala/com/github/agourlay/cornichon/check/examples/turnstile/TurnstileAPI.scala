package com.github.agourlay.cornichon.check.examples.turnstile

import com.github.agourlay.cornichon.check.examples.HttpServer
import monix.eval.Task
import monix.execution.atomic.AtomicBoolean
import monix.execution.{ CancelableFuture, Scheduler }
import org.http4s._
import org.http4s.implicits._
import org.http4s.dsl._
import org.http4s.server.Router
import org.http4s.server.blaze.BlazeServerBuilder

class TurnstileAPI extends Http4sDsl[Task] {

  implicit val s = Scheduler.Implicits.global

  private val turnstileLocked = AtomicBoolean(true)

  private val turnstileService = HttpRoutes.of[Task] {
    case POST -> Root / "push-coin" ⇒
      if (turnstileLocked.get()) {
        turnstileLocked.set(false)
        Ok("payment accepted")
      } else
        BadRequest("payment refused")

    case POST -> Root / "walk-through" ⇒
      if (turnstileLocked.get())
        BadRequest("door blocked")
      else {
        turnstileLocked.set(true)
        Ok("door turns")
      }
  }

  private val routes = Router(
    "/" -> turnstileService
  )

  def start(httpPort: Int): CancelableFuture[HttpServer] =
    BlazeServerBuilder[Task]
      .bindHttp(httpPort, "localhost")
      .withHttpApp(routes.orNotFound)
      .allocate
      .map { case (_, stop) ⇒ new HttpServer(stop) }
      .runToFuture

}

