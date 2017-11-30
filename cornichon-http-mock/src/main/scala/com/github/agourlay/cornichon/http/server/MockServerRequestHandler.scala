package com.github.agourlay.cornichon.http.server

import cats.instances.string._
import cats.syntax.either._
import com.github.agourlay.cornichon.core.{ CornichonException, Done }
import com.github.agourlay.cornichon.http.{ HttpMethod, HttpMethods, HttpRequest }
import com.github.agourlay.cornichon.json.CornichonJson
import fs2.{ Scheduler, Strategy, Task }
import io.circe.Json
import org.http4s._
import org.http4s.circe._
import org.http4s.dsl._

import scala.collection.breakOut
import scala.concurrent.duration._

class MockServerRequestHandler(implicit strategy: Strategy, scheduler: Scheduler) {

  private val mockState = new MockServerStateHolder()

  def fetchRecordedRequestsAsJson() = mockState.getReceivedRequest.map { req ⇒
    Json.fromFields(
      Seq(
        "body" → CornichonJson.parseJsonUnsafe(req.body.getOrElse("")),
        "url" → Json.fromString(req.url),
        "method" → Json.fromString(req.method.name),
        "parameters" → Json.fromFields(req.params.map { case (n, v) ⇒ (n, Json.fromString(v)) }),
        "headers" → Json.fromFields(req.headers.map { case (n, v) ⇒ (n, Json.fromString(v)) })
      )
    )
  }

  val mockService = HttpService {
    case GET -> Root / "requests-received" ⇒
      val reqs = fetchRecordedRequestsAsJson()
      val body = Json.fromValues(reqs)
      Ok(body)

    case GET -> Root / "reset" ⇒
      mockState.clearRegisteredRequest()
      Ok()

    case r @ POST -> Root / "response" ⇒
      r.bodyAsText.runFold("")(_ ++ _).flatMap { body ⇒
        mockState.setResponse(body)
        Ok()
      }

    case r @ POST -> Root / "delayInMs" ⇒
      r.bodyAsText.runFold("")(_ ++ _).flatMap { body ⇒
        // Dropping extra quotes
        Either.catchNonFatal(body.substring(1, body.length - 1).toLong) match {
          case Right(delay) ⇒
            mockState.setDelay(delay)
            Ok()
          case Left(_) ⇒
            BadRequest(s"$body is not a valid delay value")
        }
      }

    case POST -> Root / "toggle-error-mode" ⇒
      mockState.toggleErrorMode
      Ok()

    case POST -> Root / "toggle-bad-request-mode" ⇒
      mockState.toggleBadRequestMode
      Ok()

    case _ if mockState.getErrorMode ⇒
      replyWithDeplay(InternalServerError(mockState.getResponse))

    case _ if mockState.getBadRequestMode ⇒
      replyWithDeplay(BadRequest(mockState.getResponse))

    case r @ POST -> _ ⇒
      saveRequest(r).flatMap(_ ⇒ replyWithDeplay(Created(mockState.getResponse)))

    case r @ _ -> _ ⇒
      saveRequest(r).flatMap(_ ⇒ replyWithDeplay(Ok(mockState.getResponse)))
  }

  def replyWithDeplay(t: Task[Response]): Task[Response] =
    if (mockState.getDelay == 0)
      t
    else
      Task.schedule(Done, mockState.getDelay.millis).flatMap(_ ⇒ t)

  def httpMethodMapper(method: Method): HttpMethod = method match {
    case DELETE  ⇒ HttpMethods.DELETE
    case GET     ⇒ HttpMethods.GET
    case HEAD    ⇒ HttpMethods.HEAD
    case OPTIONS ⇒ HttpMethods.OPTIONS
    case PATCH   ⇒ HttpMethods.PATCH
    case POST    ⇒ HttpMethods.POST
    case PUT     ⇒ HttpMethods.PUT
    case other   ⇒ throw CornichonException(s"unsupported HTTP method ${other.name}")
  }

  def saveRequest(rawReq: Request) =
    rawReq
      .bodyAsText
      .runFold("")(_ ++ _)
      .map { decodedBody ⇒
        val req = HttpRequest[String](
          method = httpMethodMapper(rawReq.method),
          url = rawReq.uri.path.toString(),
          body = Some(decodedBody),
          params = rawReq.params.toList,
          headers = rawReq.headers.map(h ⇒ (h.name.value, h.value))(breakOut)
        )
        mockState.registerRequest(req)
      }
}
