package com.github.agourlay.cornichon.core

import cats.data.Xor

import scala.concurrent.ExecutionContext

trait Step {
  def title: String
  def run(engine: Engine)(initialRunState: RunState)(implicit ec: ExecutionContext): (RunState, FailedStep Xor Done)
}

trait WrapperStep extends Step {
  def nested: Vector[Step]

  def failedTitleLog(depth: Int) = FailureLogInstruction(title, depth)
  def successTitleLog(depth: Int) = SuccessLogInstruction(title, depth)
}

