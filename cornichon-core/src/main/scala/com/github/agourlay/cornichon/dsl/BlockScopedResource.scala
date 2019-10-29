package com.github.agourlay.cornichon.dsl

import com.github.agourlay.cornichon.core.{ RunState, Session }
import monix.eval.Task

trait BlockScopedResource {

  val sessionTarget: String

  val openingTitle: String
  val closingTitle: String

  def use[A](outsideRunState: RunState)(runInside: RunState => Task[A]): Task[(Session, A)]
}