package com.github.agourlay.cornichon.http.server

import cats.instances.string._
import cats.syntax.either._

import com.github.agourlay.cornichon.core.{ CornichonException, Done }
import com.github.agourlay.cornichon.http.{ HttpMethod, HttpMethods, HttpRequest }
import com.github.agourlay.cornichon.json.CornichonJson

import io.circe.Json

import monix.eval.Task
import monix.eval.Task._

import org.http4s._
import org.http4s.circe._
import org.http4s.dsl.Http4sDsl

import scala.concurrent.duration._

class MockServerRequestHandler() extends Http4sDsl[Task] {

  private val mockState = new MockServerStateHolder()

  def fetchRecordedRequestsAsJson(): Vector[Json] = mockState.getReceivedRequest.map { req =>
    Json.fromFields(
      Seq(
        "body" → CornichonJson.parseDslJsonUnsafe(req.body.getOrElse("")),
        "url" → Json.fromString(req.url),
        "method" → Json.fromString(req.method.name),
        "parameters" → Json.fromFields(req.params.map { case (n, v) => (n, Json.fromString(v)) }),
        "headers" → Json.fromFields(req.headers.map { case (n, v) => (n, Json.fromString(v)) })
      )
    )
  }

  val mockService = HttpRoutes.of[Task] {
    case GET -> Root / "requests-received" =>
      val reqs = fetchRecordedRequestsAsJson()
      val body = Json.fromValues(reqs)
      Ok(body)

    case GET -> Root / "reset" =>
      mockState.clearRegisteredRequest()
      Ok()

    case r @ POST -> Root / "response" =>
      r.bodyAsText.compile.fold("")(_ ++ _).flatMap { body =>
        mockState.setResponse(body)
        Ok()
      }

    case r @ POST -> Root / "delayInMs" =>
      r.bodyAsText.compile.fold("")(_ ++ _).flatMap { body =>
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

  def replyWithDelay(t: Task[Response[Task]]): Task[Response[Task]] =
    if (mockState.getDelay == 0)
      t
    else
      Task.now(Done).delayExecution(mockState.getDelay.millis).flatMap(_ => t)

  def httpMethodMapper(method: Method): HttpMethod = method match {
    case DELETE  => HttpMethods.DELETE
    case GET     => HttpMethods.GET
    case HEAD    => HttpMethods.HEAD
    case OPTIONS => HttpMethods.OPTIONS
    case PATCH   => HttpMethods.PATCH
    case POST    => HttpMethods.POST
    case PUT     => HttpMethods.PUT
    case other   => throw CornichonException(s"unsupported HTTP method ${other.name}")
  }

  def saveRequest(rawReq: Request[Task]): Task[Boolean] =
    rawReq
      .bodyAsText
      .compile
      .fold("")(_ ++ _)
      .map { decodedBody =>
        val req = HttpRequest[String](
          method = httpMethodMapper(rawReq.method),
          url = rawReq.uri.path.toString,
          body = Some(decodedBody),
          params = rawReq.params.toList,
          headers = rawReq.headers.toList.map(h => (h.name.value, h.value))
        )
        mockState.registerRequest(req)
      }
}
