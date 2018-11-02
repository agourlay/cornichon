package com.github.agourlay.cornichon.check.examples

import cats.effect.ExitCode
import cats.effect.concurrent.Ref
import fs2.concurrent.SignallingRef
import monix.eval.Task
import monix.execution.{ CancelableFuture, Scheduler }
import org.http4s._
import org.http4s.dsl._
import org.http4s.server.Router
import org.http4s.server.blaze.BlazeServerBuilder

class ReverseAPI extends Http4sDsl[Task] {

  implicit val s = Scheduler.Implicits.global

  object WordQueryParamMatcher extends QueryParamDecoderMatcher[String]("word")

  private val reverseService = HttpRoutes.of[Task] {
    case POST -> Root / "double-reverse" :? WordQueryParamMatcher(word) ⇒
      Ok(word.reverse.reverse)
  }

  private val routes = Router(
    "/" -> reverseService
  )

  def start(httpPort: Int): CancelableFuture[HttpServer] =
    SignallingRef[Task, Boolean](false).map { signal ⇒

      // The process is started without binding and shutdown through a Signal
      BlazeServerBuilder[Task]
        .bindHttp(httpPort, "localhost")
        .withHttpApp(routes.orNotFound)
        .serveWhile(signal, Ref.unsafe(ExitCode.Success))
        .compile
        .drain
        .runToFuture

      new HttpServer(signal)
    }.runToFuture(s)
}

class HttpServer(signal: SignallingRef[Task, Boolean])(implicit s: Scheduler) {
  def shutdown(): CancelableFuture[Unit] = signal.set(true).runToFuture
}

