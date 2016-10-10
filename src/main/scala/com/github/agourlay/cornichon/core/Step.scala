package com.github.agourlay.cornichon.core

import cats.data.Xor

import scala.concurrent.{ ExecutionContext, Future }

trait Step {
  def title: String
  def setTitle(newTitle: String): Step
  def run(engine: Engine)(initialRunState: RunState)(implicit ec: ExecutionContext): Future[(RunState, FailedStep Xor Done)]
}

trait WrapperStep extends Step {
  def nested: Vector[Step]

  // Without effect by default - wrapper steps usually build dynamically their title
  def setTitle(newTitle: String) = this
  def failedTitleLog(depth: Int) = FailureLogInstruction(title, depth)
  def successTitleLog(depth: Int) = SuccessLogInstruction(title, depth)
}

