package com.github.agourlay.cornichon.http.server

import com.github.agourlay.cornichon.CornichonFeature
import com.github.agourlay.cornichon.core.Session
import com.github.agourlay.cornichon.dsl.{ BlockScopedResource, ResourceHandle }
import com.github.agourlay.cornichon.http.server.HttpMockServerResource.SessionKeys._
import com.github.agourlay.cornichon.json.CornichonJson
import com.github.agourlay.cornichon.util.ShowInstances._
import io.circe.Json

case class HttpMockServerResource(label: String, port: Int) extends BlockScopedResource {
  val sessionTarget: String = label
  val openingTitle: String = s"Starting HTTP mock server '$label' listening on port '$port'"
  val closingTitle: String = s"Shutting down HTTP mock server '$label'"

  implicit val (_, ec, system, mat) = CornichonFeature.globalRuntime

  def startResource() = {
    CornichonFeature.reserveGlobalRuntime()
    val mockRequestHandler = MockServerRequestHandler(label, port)
    val akkaServer = new AkkaHttpServer(port, mockRequestHandler.requestReceivedRepo, mockRequestHandler.requestHandler)
    akkaServer.startServer().map { serverCloseHandler ⇒
      new ResourceHandle {
        def resourceResults() = requestsResults(mockRequestHandler)
        def stopResource() = serverCloseHandler.stopResource().map { _ ⇒
          mockRequestHandler.shutdown()
          CornichonFeature.releaseGlobalRuntime()
        }
      }
    }
  }

  def requestsResults(mockRequestHandler: MockServerRequestHandler) =
    mockRequestHandler.getRecordedRequests.map { requests ⇒
      val jsonRequests = requests.map { req ⇒
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
      Session.newEmpty
        .addValue(s"$sessionTarget$receivedBodiesSuffix", Json.fromValues(jsonRequests).spaces2)
        .addValue(s"$sessionTarget$nbReceivedCallsSuffix", requests.size.toString)
    }
}

object HttpMockServerResource {
  object SessionKeys {
    val nbReceivedCallsSuffix = "-nb-received-calls"
    val receivedBodiesSuffix = "-received-bodies"
  }
}
