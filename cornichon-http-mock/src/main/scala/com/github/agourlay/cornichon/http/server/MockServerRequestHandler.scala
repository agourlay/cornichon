package com.github.agourlay.cornichon.http.server

import cats.syntax.either._
import cats.effect.IO
import com.github.agourlay.cornichon.core.{ CornichonException, Done }
import com.github.agourlay.cornichon.http.{ HttpMethod, HttpMethods, HttpRequest }
import com.github.agourlay.cornichon.json.CornichonJson

import io.circe.Json

import org.http4s._
import org.http4s.circe._
import org.http4s.dsl.Http4sDsl

import scala.concurrent.duration._

class MockServerRequestHandler extends Http4sDsl[IO] {

  private val mockState = new MockServerStateHolder()

  def fetchRecordedRequestsAsJson(): Vector[Json] = mockState.getReceivedRequest.map { req =>
    Json.fromFields(
      Seq(
        "body" -> CornichonJson.parseDslJsonUnsafe(req.body.getOrElse("")),
        "url" -> Json.fromString(req.url),
        "method" -> Json.fromString(req.method.name),
        "parameters" -> Json.fromFields(req.params.map { case (n, v) => (n, Json.fromString(v)) }),
        "headers" -> Json.fromFields(req.headers.map { case (n, v) => (n, Json.fromString(v)) })
      )
    )
  }

  val mockService = HttpRoutes.of[IO] {
    case GET -> Root / "requests-received" =>
      val reqs = fetchRecordedRequestsAsJson()
      val body = Json.fromValues(reqs)
      Ok(body)

    case GET -> Root / "reset" =>
      mockState.clearRegisteredRequest()
      Ok()

    case r @ POST -> Root / "response" =>
      r.bodyText.compile.string.flatMap { body =>
        mockState.setResponse(body)
        Ok()
      }

    case r @ POST -> Root / "delayInMs" =>
      r.bodyText.compile.string.flatMap { body =>
        // Dropping extra quotes
        Either.catchNonFatal(body.substring(1, body.length - 1).toLong) match {
          case Right(delay) =>
            mockState.setDelay(delay)
            Ok()
          case Left(_) =>
            BadRequest(s"$body is not a valid delay value")
        }
      }

    case POST -> Root / "toggle-error-mode" =>
      mockState.toggleErrorMode()
      Ok()

    case POST -> Root / "toggle-bad-request-mode" =>
      mockState.toggleBadRequestMode()
      Ok()

    case _ if mockState.getErrorMode =>
      replyWithDelay(InternalServerError(mockState.getResponse))

    case _ if mockState.getBadRequestMode =>
      replyWithDelay(BadRequest(mockState.getResponse))

    case r @ POST -> _ =>
      saveRequest(r).flatMap(_ => replyWithDelay(Created(mockState.getResponse)))

    case r @ _ -> _ =>
      saveRequest(r).flatMap(_ => replyWithDelay(Ok(mockState.getResponse)))
  }

  private def replyWithDelay(t: IO[Response[IO]]): IO[Response[IO]] =
    if (mockState.getDelay == 0)
      t
    else
      IO.delay(Done).delayBy(mockState.getDelay.millis).flatMap(_ => t)

  private def httpMethodMapper(method: Method): HttpMethod = method match {
    case DELETE  => HttpMethods.DELETE
    case GET     => HttpMethods.GET
    case HEAD    => HttpMethods.HEAD
    case OPTIONS => HttpMethods.OPTIONS
    case PATCH   => HttpMethods.PATCH
    case POST    => HttpMethods.POST
    case PUT     => HttpMethods.PUT
    case other   => throw CornichonException(s"unsupported HTTP method ${other.name}")
  }

  private def saveRequest(rawReq: Request[IO]): IO[Boolean] =
    rawReq
      .bodyText
      .compile
      .string
      .map { decodedBody =>
        val req = HttpRequest[String](
          method = httpMethodMapper(rawReq.method),
          url = rawReq.uri.path.renderString,
          body = Some(decodedBody),
          params = rawReq.params.toList,
          headers = rawReq.headers.headers.map(h => (h.name.toString, h.value))
        )
        mockState.registerRequest(req)
      }
}
