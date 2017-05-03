package com.github.agourlay.cornichon.core

import monix.execution.Scheduler

import scala.concurrent.Future

trait Step {
  def title: String
  def setTitle(newTitle: String): Step
  def run(engine: Engine)(initialRunState: RunState)(implicit scheduler: Scheduler): Future[(RunState, FailedStep Either Done)]
}

trait WrapperStep extends Step {
  def nested: List[Step]

  // Without effect by default - wrapper steps usually build dynamically their title
  def setTitle(newTitle: String) = this
  def failedTitleLog(depth: Int) = FailureLogInstruction(title, depth)
  def successTitleLog(depth: Int) = SuccessLogInstruction(title, depth)
}

