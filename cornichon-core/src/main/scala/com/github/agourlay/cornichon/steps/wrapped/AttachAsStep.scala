package com.github.agourlay.cornichon.steps.wrapped

import cats.data.Chain
import com.github.agourlay.cornichon.core._

import scala.concurrent.duration.Duration

// Steps are wrapped/indented with a specific title
case class AttachAsStep(title: String, nested: List[Step]) extends LogDecoratorStep {

  override def setTitle(newTitle: String) = copy(title = newTitle)

  val nestedToRun = nested

  override def onNestedError(resultLogs: Chain[LogInstruction], depth: Int, executionTime: Duration) =
    failedTitleLog(depth) +: resultLogs :+ FailureLogInstruction(s"$title - Failed", depth)

  override def onNestedSuccess(resultLogs: Chain[LogInstruction], depth: Int, executionTime: Duration) =
    successTitleLog(depth) +: resultLogs :+ SuccessLogInstruction(s"$title - Succeeded", depth, Some(executionTime))
}
