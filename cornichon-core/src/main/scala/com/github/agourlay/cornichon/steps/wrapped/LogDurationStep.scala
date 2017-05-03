package com.github.agourlay.cornichon.steps.wrapped

import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.util.Timing._

import monix.execution.Scheduler

case class LogDurationStep(nested: List[Step], label: String) extends WrapperStep {

  val title = s"Log duration block with label '$label' started"

  override def run(engine: Engine)(initialRunState: RunState)(implicit scheduler: Scheduler) = {
    withDuration {
      val logRunState = initialRunState.forNestedSteps(nested)
      engine.runSteps(logRunState)
    }.map {
      case ((logState, repeatRes), executionTime) ⇒
        val titleLog = DebugLogInstruction(title, initialRunState.depth)
        val fullLogs = titleLog +: logState.logs :+ DebugLogInstruction(s"Log duration block with label '$label' ended", initialRunState.depth, Some(executionTime))
        (initialRunState.withSession(logState.session).appendLogs(fullLogs), repeatRes)
    }
  }
}
