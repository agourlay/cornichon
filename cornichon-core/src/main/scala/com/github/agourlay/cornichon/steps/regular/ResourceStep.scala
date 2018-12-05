package com.github.agourlay.cornichon.steps.regular

import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.steps.wrapped.AttachAsStep

import scala.concurrent.duration.Duration

case class ResourceStep(title: String, acquire: Step, release: Step) extends SimpleWrapperStep {
  override val nestedToRun = AttachAsStep(s"$title - acquire step", acquire :: Nil) :: Nil
  override val indentLog = false

  def onNestedError(failedStep: FailedStep, resultRunState: RunState, initialRunState: RunState, executionTime: Duration) =
    (initialRunState.mergeNested(resultRunState), failedStep)

  def onNestedSuccess(resultRunState: RunState, initialRunState: RunState, executionTime: Duration) =
    initialRunState.mergeNested(resultRunState).registerCleanupStep(AttachAsStep(s"$title - release step", release :: Nil))
}
