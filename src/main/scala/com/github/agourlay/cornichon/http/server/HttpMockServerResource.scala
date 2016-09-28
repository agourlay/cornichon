package com.github.agourlay.cornichon.http.server

import com.github.agourlay.cornichon.CornichonFeature
import com.github.agourlay.cornichon.core.Session
import com.github.agourlay.cornichon.dsl.{ BlockScopedResource, ResourceHandle }
import com.github.agourlay.cornichon.http.server.HttpMockServerResource.SessionKeys._
import io.circe.Json

case class HttpMockServerResource(label: String, port: Int) extends BlockScopedResource {
  val sessionTarget: String = label
  val openingTitle: String = s"Starting HTTP mock server '$label' listening on port '$port'"
  val closingTitle: String = s"Shutting down HTTP mock server '$label'"

  implicit val (_, ec, system, mat) = CornichonFeature.globalRuntime

  def startResource() = {
    CornichonFeature.reserveGlobalRuntime()
    val mockRequestHandler = MockServerRequestHandler(label, port)
    val akkaServer = new AkkaHttpServer(port, mockRequestHandler.requestHandler)
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
    mockRequestHandler.fetchRecordedRequestsAsJson().map { jsonRequests ⇒
      Session.newEmpty
        .addValue(s"$sessionTarget$receivedBodiesSuffix", Json.fromValues(jsonRequests).spaces2)
        .addValue(s"$sessionTarget$nbReceivedCallsSuffix", jsonRequests.size.toString)
    }
}

object HttpMockServerResource {
  object SessionKeys {
    val nbReceivedCallsSuffix = "-nb-received-calls"
    val receivedBodiesSuffix = "-received-bodies"
  }
}
