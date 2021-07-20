package com.github.agourlay.cornichon.framework.examples.propertyCheck.stringReverse

import com.github.agourlay.cornichon.framework.examples.HttpServer
import monix.eval.Task
import monix.execution.{ CancelableFuture, Scheduler }
import org.http4s._
import org.http4s.implicits._
import org.http4s.dsl._
import org.http4s.server.Router
import org.http4s.blaze.server.BlazeServerBuilder

class ReverseAPI extends Http4sDsl[Task] {

  implicit val s = Scheduler.Implicits.global

  object WordQueryParamMatcher extends QueryParamDecoderMatcher[String]("word")

  private val reverseService = HttpRoutes.of[Task] {
    case POST -> Root / "double-reverse" :? WordQueryParamMatcher(word) =>
      Ok(word.reverse.reverse)
  }

  private val routes = Router(
    "/" -> reverseService
  )

  def start(httpPort: Int): CancelableFuture[HttpServer] =
    BlazeServerBuilder[Task](executionContext = s)
      .bindHttp(httpPort, "localhost")
      .withoutBanner
      .withHttpApp(routes.orNotFound)
      .allocated
      .map { case (_, stop) => new HttpServer(stop) }
      .runToFuture
}

