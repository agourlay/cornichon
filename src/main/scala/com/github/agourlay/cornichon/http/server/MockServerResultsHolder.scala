package com.github.agourlay.cornichon.http.server

import akka.actor.{ Actor, Props }
import com.github.agourlay.cornichon.core.Done
import com.github.agourlay.cornichon.http
import com.github.agourlay.cornichon.http.HttpRequest
import com.github.agourlay.cornichon.http.server.MockServerResultsHolder._

class MockServerResultsHolder extends Actor {

  private val receivedRequests = scala.collection.mutable.ArrayBuffer.empty[http.HttpRequest[String]]

  def receive = {

    case GetReceivedRequest ⇒
      sender ! RegisteredRequests(receivedRequests.toVector)

    case RegisterRequest(req) ⇒
      receivedRequests.+=(req)
      sender ! RequestRegistered(req)

    case ClearRegisteredRequest ⇒
      receivedRequests.clear()
      sender ! Done
  }
}

object MockServerResultsHolder {
  def props() = Props(classOf[MockServerResultsHolder])
  case object ClearRegisteredRequest
  case object GetReceivedRequest
  case class RequestRegistered(request: HttpRequest[String])
  case class RegisteredRequests(requests: Vector[HttpRequest[String]])
  case class RegisterRequest(request: HttpRequest[String])
}

