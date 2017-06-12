package com.github.agourlay.cornichon.steps.wrapped

import com.github.agourlay.cornichon.core._

import scala.concurrent.duration.Duration

case class LogDurationStep(nested: List[Step], label: String) extends SimpleWrapperStep {

  val title = s"Log duration block with label '$label' started"

  override def nestedToRun: List[Step] = nested

  override def onNestedError(failedStep: FailedStep, result: RunState, initialRunState: RunState, executionTime: Duration) = {
    val titleLog = DebugLogInstruction(title, initialRunState.depth)
    val fullLogs = titleLog +: result.logs :+ DebugLogInstruction(s"Log duration block with label '$label' ended", initialRunState.depth, Some(executionTime))
    (initialRunState.withSession(result.session).appendLogs(fullLogs), failedStep)
  }

  override def onNestedSuccess(result: RunState, initialRunState: RunState, executionTime: Duration): RunState = {
    val titleLog = DebugLogInstruction(title, initialRunState.depth)
    val fullLogs = titleLog +: result.logs :+ DebugLogInstruction(s"Log duration block with label '$label' ended", initialRunState.depth, Some(executionTime))
    initialRunState.withSession(result.session).appendLogs(fullLogs)
  }
}
