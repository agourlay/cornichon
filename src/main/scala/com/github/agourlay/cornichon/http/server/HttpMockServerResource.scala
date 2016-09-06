package com.github.agourlay.cornichon.http.server

import com.github.agourlay.cornichon.CornichonFeature
import com.github.agourlay.cornichon.core.Session
import com.github.agourlay.cornichon.dsl.BlockScopedResource
import com.github.agourlay.cornichon.http.server.HttpMockServerResource.SessionKeys._
import com.github.agourlay.cornichon.json.CornichonJson
import io.circe.Json

case class HttpMockServerResource(label: String, port: Int) extends BlockScopedResource {
  val sessionTarget: String = label
  val openingTitle: String = s"Starting HTTP mock server '$label' listening on port '$port'"
  val closingTitle: String = s"Shutting down HTTP mock server '$label'"

  // FIXME vars :(
  var akkaServer: AkkaHttpServer = _
  var mockRequestHandler: MockRequestHandler = _

  def startResource() = {
    CornichonFeature.reserveGlobalRuntime()
    implicit val (_, ec, system, mat) = CornichonFeature.globalRuntime
    mockRequestHandler = new MockRequestHandler()
    akkaServer = new AkkaHttpServer(port, mockRequestHandler.requestHandler)
    akkaServer.startServer
  }

  def stopResource() = {
    CornichonFeature.releaseGlobalRuntime()
    akkaServer.stopServer
  }

  def resourceResults() = {
    val requests = mockRequestHandler.getRecordedRequests
    val jsonRequests = requests.map { req ⇒
      val fields = Seq(
        "body" → CornichonJson.parseJsonUnsafe(req.body.getOrElse("")),
        "url" → Json.fromString(req.url),
        "verb" → Json.fromString(req.method.name)
      )
      Json.fromFields(fields)
    }
    Session.newSession
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
