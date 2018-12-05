package com.github.agourlay.cornichon.steps.wrapped

import com.github.agourlay.cornichon.core._

import scala.concurrent.duration.Duration

case class LogDurationStep(nested: List[Step], label: String) extends LogDecoratorStep {

  val title = s"Log duration block with label '$label' started"

  val nestedToRun = nested

  override def logStackOnNestedError(resultLogStack: List[LogInstruction], depth: Int, executionTime: Duration): List[LogInstruction] = {
    val titleLog = DebugLogInstruction(title, depth)
    DebugLogInstruction(s"Log duration block with label '$label' ended", depth, Some(executionTime)) +: resultLogStack :+ titleLog
  }

  override def logStackOnNestedSuccess(resultLogStack: List[LogInstruction], depth: Int, executionTime: Duration): List[LogInstruction] = {
    val titleLog = DebugLogInstruction(title, depth)
    DebugLogInstruction(s"Log duration block with label '$label' ended", depth, Some(executionTime)) +: resultLogStack :+ titleLog
  }
}
