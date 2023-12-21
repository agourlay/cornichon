package com.github.agourlay.cornichon.framework

import sbt.testing.{ Event, EventHandler }

import scala.collection.concurrent.TrieMap

class RecordEventHandler extends EventHandler {
  private val events = new TrieMap[String, Event]()
  def handle(event: Event): Unit = events += (event.fullyQualifiedName() -> event)
  def recorded: List[Event] = events.values.toList
}