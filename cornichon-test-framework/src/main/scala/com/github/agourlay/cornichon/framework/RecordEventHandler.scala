package com.github.agourlay.cornichon.framework

import sbt.testing.{Event, EventHandler}

import java.util.concurrent.ConcurrentLinkedQueue
import scala.jdk.CollectionConverters._

// Every scenario event carries the *feature* class as its `fullyQualifiedName` - the scenario name
// lives in the selector. Keying by name would therefore collapse a whole feature down to whichever
// scenario finished last, so all events are kept instead, in arrival order.
// Scenarios run concurrently, hence the concurrent queue.
class RecordEventHandler extends EventHandler {
  private val events = new ConcurrentLinkedQueue[Event]()
  def handle(event: Event): Unit = { val _ = events.add(event) }
  def recorded: List[Event] = events.iterator().asScala.toList
}
