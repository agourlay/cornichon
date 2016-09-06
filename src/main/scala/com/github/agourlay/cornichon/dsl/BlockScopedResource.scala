package com.github.agourlay.cornichon.dsl

import com.github.agourlay.cornichon.core.Session

import scala.concurrent.Future

trait BlockScopedResource {

  val sessionTarget: String

  val openingTitle: String
  val closingTitle: String

  def startResource(): Future[Unit]

  def stopResource(): Future[Unit]

  def resourceResults(): Session
}
