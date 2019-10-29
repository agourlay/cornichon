package com.github.agourlay.cornichon.http.server

import com.github.agourlay.cornichon.core.{ RunState, Session }
import com.github.agourlay.cornichon.dsl.BlockScopedResource
import com.github.agourlay.cornichon.http.server.HttpMockServerResource.SessionKeys._
import io.circe.Json
import monix.eval.Task
import monix.execution.Scheduler

case class HttpMockServerResource(interface: Option[String], label: String, portRange: Option[Range])
  extends BlockScopedResource {

  implicit val scheduler = Scheduler.Implicits.global

  val sessionTarget: String = label
  val openingTitle: String = s"Starting HTTP mock server '$label'"
  val closingTitle: String = s"Shutting down HTTP mock server '$label'"

  def use[A](outsideRunState: RunState)(f: RunState => Task[A]): Task[(Session, A)] = {
    val mockRequestHandler = new MockServerRequestHandler()

    val initSession: String => Session = id => Session.newEmpty.addValueUnsafe(s"$label-url", id)
    val resourceContext: RunState => Session => RunState = r1 => s1 => r1.mergeSessions(s1)
    val runWithServer = initSession.andThen(resourceContext(outsideRunState)).andThen(f)

    val mockServer = new MockHttpServer(interface, portRange, mockRequestHandler.mockService)(runWithServer)

    mockServer.useServer().map { res =>
      val resourceResults = requestsResults(mockRequestHandler)
      (resourceResults, res)
    }
  }

  def requestsResults(mockRequestHandler: MockServerRequestHandler): Session = {
    val jsonRequests = mockRequestHandler.fetchRecordedRequestsAsJson()
    Session.newEmpty
      .addValueUnsafe(s"$sessionTarget$receivedBodiesSuffix", Json.fromValues(jsonRequests).spaces2)
      .addValueUnsafe(s"$sessionTarget$nbReceivedCallsSuffix", jsonRequests.size.toString)
  }
}

object HttpMockServerResource {
  object SessionKeys {
    val nbReceivedCallsSuffix = "-nb-received-calls"
    val receivedBodiesSuffix = "-received-bodies"
  }
}
