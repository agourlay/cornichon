package com.github.agourlay.cornichon.http.server

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.{AtomicBoolean, AtomicLong, AtomicReference}

import com.github.agourlay.cornichon.http
import scala.jdk.CollectionConverters._

class MockServerStateHolder {

  private val receivedRequests = new ConcurrentLinkedQueue[http.HttpRequest[String]]()
  private val errorMode = new AtomicBoolean(false)
  private val badRequestMode = new AtomicBoolean(false)
  private val response = new AtomicReference[String]("")
  private val delay = new AtomicLong(0)

  def getReceivedRequest = receivedRequests.asScala.toVector

  def registerRequest(req: http.HttpRequest[String]) = receivedRequests.add(req)

  def clearRegisteredRequest() = receivedRequests.clear()

  @scala.annotation.tailrec
  final def toggleErrorMode(): Unit = {
    val current = errorMode.get()
    if (!errorMode.compareAndSet(current, !current)) toggleErrorMode()
  }

  def getErrorMode = errorMode.get

  @scala.annotation.tailrec
  final def toggleBadRequestMode(): Unit = {
    val current = badRequestMode.get()
    if (!badRequestMode.compareAndSet(current, !current)) toggleBadRequestMode()
  }

  def getBadRequestMode = badRequestMode.get

  def setResponse(newResponse: String) = response.set(newResponse)

  def getResponse: String = response.get

  def setDelay(newDelay: Long) = delay.set(newDelay)

  def getDelay: Long = delay.get

}
