package com.github.agourlay.cornichon.steps.wrapped

import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.util.Timing._

import scala.concurrent.ExecutionContext

case class LogDurationStep(nested: Vector[Step], label: String) extends WrapperStep {

  val title = s"Log duration block with label '$label' started"

  override def run(engine: Engine)(initialRunState: RunState)(implicit ec: ExecutionContext) = {
    val ((logState, repeatRes), executionTime) = withDuration {
      val logRunState = initialRunState.withSteps(nested).resetLogs.goDeeper
      engine.runSteps(logRunState)
    }
    val titleLog = DebugLogInstruction(title, initialRunState.depth)
    val fullLogs = titleLog +: logState.logs :+ DebugLogInstruction(s"Log duration block with label '$label' ended", initialRunState.depth, Some(executionTime))
    (initialRunState.withSession(logState.session).appendLogs(fullLogs), repeatRes)
  }

}
