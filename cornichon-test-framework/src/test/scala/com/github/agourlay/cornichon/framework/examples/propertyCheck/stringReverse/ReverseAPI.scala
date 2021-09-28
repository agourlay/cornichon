package com.github.agourlay.cornichon.framework.examples.propertyCheck.stringReverse

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import com.github.agourlay.cornichon.framework.examples.HttpServer
import org.http4s._
import org.http4s.implicits._
import org.http4s.dsl._
import org.http4s.server.Router
import org.http4s.blaze.server.BlazeServerBuilder

import scala.concurrent.Future

class ReverseAPI extends Http4sDsl[IO] {

  object WordQueryParamMatcher extends QueryParamDecoderMatcher[String]("word")

  private val reverseService = HttpRoutes.of[IO] {
    case POST -> Root / "double-reverse" :? WordQueryParamMatcher(word) =>
      Ok(word.reverse.reverse)
  }

  private val routes = Router(
    "/" -> reverseService
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

