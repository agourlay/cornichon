package com.github.agourlay.cornichon.steps.wrapped

import com.github.agourlay.cornichon.core._

import scala.concurrent.duration.Duration

// Steps are wrapped/indented with a specific title
case class AttachAsStep(title: String, nested: List[Step]) extends LogDecoratorStep {

  override def setTitle(newTitle: String) = copy(title = newTitle)

  val nestedToRun = nested

  override def logStackOnNestedError(resultLogStack: List[LogInstruction], depth: Int, executionTime: Duration): List[LogInstruction] =
    failedTitleLog(depth) +: resultLogStack :+ FailureLogInstruction(s"$title - Failed", depth, Some(executionTime))

  override def logStackOnNestedSuccess(resultLogStack: List[LogInstruction], depth: Int, executionTime: Duration): List[LogInstruction] =
    successTitleLog(depth) +: resultLogStack :+ SuccessLogInstruction(s"$title - Succeeded", depth, Some(executionTime))
}
