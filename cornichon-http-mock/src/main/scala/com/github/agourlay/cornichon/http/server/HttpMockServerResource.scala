package com.github.agourlay.cornichon.http.server

import cats.effect.IO
import com.github.agourlay.cornichon.core.{ RunState, Session }
import com.github.agourlay.cornichon.dsl.BlockScopedResource
import com.github.agourlay.cornichon.http.server.HttpMockServerResource.SessionKeys._
import io.circe.Json

case class HttpMockServerResource(interface: Option[String], label: String, portRange: Option[Range], maxPortBindingRetries: Int)
  extends BlockScopedResource {

  private val interfaceInfo = interface.fold("")(i => s" on interface `$i`")
  private val portsInfo = portRange.fold("")(r => s" using a port in range `${r.start}..${r.end}`")

  val sessionTarget: String = label
  val openingTitle: String = s"Starting HTTP mock server '$label'$interfaceInfo$portsInfo"
  val closingTitle: String = s"Shutting down HTTP mock server '$label'"

  def use[A](outsideRunState: RunState)(f: RunState => IO[A]): IO[(Session, A)] = {
    val mockRequestHandler = new MockServerRequestHandler()

    val initSession: String => Session = id => Session.newEmpty.addValueUnsafe(s"$label-url", id)
    val resourceContext: RunState => Session => RunState = r1 => s1 => r1.mergeSessions(s1)
    val runWithServer = initSession.andThen(resourceContext(outsideRunState)).andThen(f)

    val mockServer = new MockHttpServer(label, interface, portRange, mockRequestHandler.mockService, maxPortBindingRetries)(runWithServer)

    mockServer.useServer().map { res =>
      val resourceResults = requestsResults(mockRequestHandler)
      (resourceResults, res)
    }
  }

  private def requestsResults(mockRequestHandler: MockServerRequestHandler): Session = {
    val jsonRequests = mockRequestHandler.fetchRecordedRequestsAsJson()
    Session.newEmpty
      .addValues(
        s"$sessionTarget$receivedBodiesSuffix" -> Json.fromValues(jsonRequests).spaces2,
        s"$sessionTarget$nbReceivedCallsSuffix" -> jsonRequests.size.toString)
      .valueUnsafe
  }
}

object HttpMockServerResource {
  object SessionKeys {
    val nbReceivedCallsSuffix = "-nb-received-calls"
    val receivedBodiesSuffix = "-received-bodies"
  }
}
