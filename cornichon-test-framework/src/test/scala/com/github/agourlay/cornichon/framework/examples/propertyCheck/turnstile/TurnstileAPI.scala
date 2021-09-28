package com.github.agourlay.cornichon.framework.examples.propertyCheck.turnstile

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import com.github.agourlay.cornichon.framework.examples.HttpServer
import org.http4s._
import org.http4s.implicits._
import org.http4s.dsl._
import org.http4s.server.Router
import org.http4s.blaze.server.BlazeServerBuilder

import java.util.concurrent.atomic.AtomicBoolean
import scala.concurrent.Future

class TurnstileAPI extends Http4sDsl[IO] {

  private val turnstileLocked = new AtomicBoolean(true)

  private val turnstileService = HttpRoutes.of[IO] {
    case POST -> Root / "push-coin" =>
      if (turnstileLocked.get()) {
        turnstileLocked.set(false)
        Ok("payment accepted")
      } else
        BadRequest("payment refused")

    case POST -> Root / "walk-through" =>
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

  def start(httpPort: Int): Future[HttpServer] =
    BlazeServerBuilder[IO]
      .bindHttp(httpPort, "localhost")
      .withoutBanner
      .withHttpApp(routes.orNotFound)
      .allocated
      .map { case (_, stop) => new HttpServer(stop) }
      .unsafeToFuture()

}

