package com.github.agourlay.cornichon.http.server

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.{ AtomicBoolean, AtomicReference }

import com.github.agourlay.cornichon.http
import scala.collection.JavaConverters._

class MockServerResultsHolder {

  private val receivedRequests = new ConcurrentLinkedQueue[http.HttpRequest[String]]()
  private val errorMode = new AtomicBoolean(false)
  private val badRequestMode = new AtomicBoolean(false)
  private val response = new AtomicReference[String]("")

  def getReceivedRequest = receivedRequests.asScala.toVector

  def registerRequest(req: http.HttpRequest[String]) = receivedRequests.add(req)

  def clearRegisteredRequest() = receivedRequests.clear()

  def toggleErrorMode() = synchronized {
    errorMode.set(!errorMode.get())
  }

  def getErrorMode = errorMode.get

  def toggleBadRequestMode() = synchronized {
    badRequestMode.set(!badRequestMode.get())
  }

  def getBadRequestMode = badRequestMode.get

  def setResponse(newResponse: String) = synchronized {
    response.set(newResponse)
  }

  def getResponse: String = response.get

}

