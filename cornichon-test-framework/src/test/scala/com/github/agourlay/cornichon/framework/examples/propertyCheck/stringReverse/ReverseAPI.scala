package com.github.agourlay.cornichon.framework.examples.propertyCheck.stringReverse

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import com.comcast.ip4s.{ Host, Port }
import com.github.agourlay.cornichon.framework.examples.HttpServer
import org.http4s._
import org.http4s.implicits._
import org.http4s.dsl._
import org.http4s.server.Router
import org.http4s.ember.server.EmberServerBuilder
import scala.concurrent.Future
import scala.concurrent.duration._

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
    Port.fromInt(httpPort) match {
      case None => Future.failed(new IllegalArgumentException("Invalid port number"))
      case Some(port) =>
        EmberServerBuilder.default[IO]
          .withPort(port)
          .withHost(Host.fromString("localhost").get)
          .withShutdownTimeout(1.seconds)
          .withHttpApp(routes.orNotFound)
          .build
          .allocated
          .map { case (_, stop) => new HttpServer(stop) }
          .unsafeToFuture()
    }
}

