package com.github.agourlay.cornichon.dsl

import com.github.agourlay.cornichon.core.Session
import monix.eval.Task

trait BlockScopedResource {

  val sessionTarget: String

  val openingTitle: String
  val closingTitle: String

  def startResource(): Task[ResourceHandle]
}

trait ResourceHandle extends CloseableResource {
  def resourceResults(): Task[Session]
  val initialisedSession: Session
}

trait CloseableResource {
  def stopResource(): Task[Unit]
}