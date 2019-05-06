package com.github.agourlay.cornichon.steps.wrapped

import com.github.agourlay.cornichon.core.{ FailedStep, RunState, SimpleWrapperStep, Step }

import scala.concurrent.duration.Duration

// `Resources` that are released at the end of the `Scenario`
case class ScenarioResourceStep(title: String, acquire: Step, release: Step) extends SimpleWrapperStep {
  override def setTitle(newTitle: String) = copy(title = newTitle)
  override val nestedToRun = AttachAsStep(s"$title - acquire step", _ ⇒ acquire :: Nil) :: Nil
  override val indentLog = false

  def onNestedError(failedStep: FailedStep, resultRunState: RunState, initialRunState: RunState, executionTime: Duration) =
    (initialRunState.mergeNested(resultRunState), failedStep)

  def onNestedSuccess(resultRunState: RunState, initialRunState: RunState, executionTime: Duration) =
    initialRunState.mergeNested(resultRunState).registerCleanupStep(AttachAsStep(s"$title - release step", _ ⇒ release :: Nil))
}
