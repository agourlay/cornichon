package com.github.agourlay.cornichon.framework.examples.nojson

import com.github.agourlay.cornichon.framework.examples.HttpServer
import monix.eval.Task
import monix.execution.{ CancelableFuture, Scheduler }
import org.http4s.implicits._
import org.http4s.dsl._
import org.http4s.EntityDecoder._
import org.http4s.HttpRoutes
import org.http4s.server.Router
import org.http4s.server.blaze.BlazeServerBuilder

class NoJsonAPI extends Http4sDsl[Task] {

  implicit val s = Scheduler.Implicits.global

  private val multipartService = HttpRoutes.of[Task] {
    case req @ POST -> Root / "multipart" =>
      req.decodeWith(multipart[Task], strict = true) { response =>
        Ok(s"Multipart file parsed successfully > ${response.parts}")
      }
  }

  private val routes = Router(
    "/" -> multipartService
  )

  def start(httpPort: Int): CancelableFuture[HttpServer] =
    BlazeServerBuilder[Task](executionContext = s)
      .bindHttp(httpPort, "localhost")
      .withoutBanner
      .withNio2(true)
      .withHttpApp(routes.orNotFound)
      .allocated
      .map { case (_, stop) => new HttpServer(stop) }
      .runToFuture
}

case class Person(name: String, age: Int)

