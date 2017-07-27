package com.github.agourlay.cornichon.steps.regular

import com.github.agourlay.cornichon.core._

import scala.concurrent.duration.Duration

case class ResourceStep[R](title: String, acquire: Step, release: Step) extends SimpleWrapperStep {
  def nestedToRun: List[Step] = List(acquire)

  def onNestedError(
    failedStep: FailedStep,
    resultRunState: RunState,
    initialRunState: RunState,
    executionTime: Duration
  ): (RunState, FailedStep) = (initialRunState, failedStep)

  def onNestedSuccess(
    resultRunState: RunState,
    initialRunState: RunState,
    executionTime: Duration
  ): RunState = resultRunState.copy(cleanupSteps = release :: resultRunState.cleanupSteps)
}
