package com.github.agourlay.cornichon.http.server

import cats.instances.string._
import com.github.agourlay.cornichon.core.CornichonException
import com.github.agourlay.cornichon.http.{ HttpMethod, HttpMethods, HttpRequest }
import com.github.agourlay.cornichon.json.CornichonJson

import io.circe.Json

import org.http4s._
import org.http4s.circe._
import org.http4s.dsl._

class MockServerRequestHandler() {

  private val requestReceivedRepo = new MockServerResultsHolder()

  def fetchRecordedRequestsAsJson() = requestReceivedRepo.getReceivedRequest.map { req ⇒
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
      requestReceivedRepo.clearRegisteredRequest()
      Ok()

    case POST -> Root / "toggle-error-mode" ⇒
      requestReceivedRepo.toggleErrorMode
      Ok()

    case _ if requestReceivedRepo.getErrorMode ⇒
      InternalServerError()

    case r @ POST -> _ ⇒
      saveRequest(r).flatMap(_ ⇒ Created())

    case r @ _ -> _ ⇒
      saveRequest(r).flatMap(_ ⇒ Ok())
  }

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
          headers = rawReq.headers.toList.map(h ⇒ (h.name.value, h.value))
        )
        requestReceivedRepo.registerRequest(req)
      }
}
