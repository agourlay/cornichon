package com.github.agourlay.cornichon.steps.wrapped

import com.github.agourlay.cornichon.core._

import scala.concurrent.duration.Duration

// Steps are wrapped/indented with a specific title
case class AttachAsStep(title: String, nested: List[Step]) extends SimpleWrapperStep {

  override def setTitle(newTitle: String) = copy(title = newTitle)

  val nestedToRun = nested

  override def onNestedError(failedStep: FailedStep, result: RunState, initialRunState: RunState, executionTime: Duration) = {
    val nestedLogs = result.logs
    val initialDepth = initialRunState.depth
    val failureLogs = failedTitleLog(initialDepth) +: nestedLogs :+ FailureLogInstruction(s"$title - Failed", initialDepth)
    (initialRunState.mergeNested(result, failureLogs), failedStep)
  }

  override def onNestedSuccess(result: RunState, initialRunState: RunState, executionTime: Duration): RunState = {
    val nestedLogs = result.logs
    val initialDepth = initialRunState.depth
    val successLogs = successTitleLog(initialDepth) +: nestedLogs :+ SuccessLogInstruction(s"$title - Succeeded", initialDepth, Some(executionTime))
    initialRunState.mergeNested(result, successLogs)
  }
}
