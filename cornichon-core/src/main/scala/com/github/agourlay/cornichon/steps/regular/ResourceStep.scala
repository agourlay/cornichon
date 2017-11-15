package com.github.agourlay.cornichon.steps.regular

import com.github.agourlay.cornichon.core._

import scala.concurrent.duration.Duration

case class ResourceStep[R](title: String, acquire: Step, release: Step) extends SimpleWrapperStep {
  val nestedToRun = acquire :: Nil

  def onNestedError(failedStep: FailedStep, resultRunState: RunState, initialRunState: RunState, executionTime: Duration) =
    (initialRunState.mergeNested(resultRunState), failedStep)

  def onNestedSuccess(resultRunState: RunState, initialRunState: RunState, executionTime: Duration) =
    initialRunState.mergeNested(resultRunState).prependCleanupStep(release)
}
