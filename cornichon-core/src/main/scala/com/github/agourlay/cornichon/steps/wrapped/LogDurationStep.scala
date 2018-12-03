package com.github.agourlay.cornichon.steps.wrapped

import cats.data.Chain
import com.github.agourlay.cornichon.core._

import scala.concurrent.duration.Duration

case class LogDurationStep(nested: List[Step], label: String) extends LogDecoratorStep {

  val title = s"Log duration block with label '$label' started"

  val nestedToRun = nested

  override def onNestedError(resultLogs: Chain[LogInstruction], depth: Int, executionTime: Duration) = {
    val titleLog = DebugLogInstruction(title, depth)
    titleLog +: resultLogs :+ DebugLogInstruction(s"Log duration block with label '$label' ended", depth, Some(executionTime))
  }

  override def onNestedSuccess(resultLogs: Chain[LogInstruction], depth: Int, executionTime: Duration) = {
    val titleLog = DebugLogInstruction(title, depth)
    titleLog +: resultLogs :+ DebugLogInstruction(s"Log duration block with label '$label' ended", depth, Some(executionTime))
  }
}
