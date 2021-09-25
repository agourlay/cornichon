package com.github.agourlay.cornichon.dsl

import com.github.agourlay.cornichon.core.{ RunState, Session }
import cats.effect.IO

trait BlockScopedResource {

  val sessionTarget: String

  val openingTitle: String
  val closingTitle: String

  def use[A](outsideRunState: RunState)(runInside: RunState => IO[A]): IO[(Session, A)]
}