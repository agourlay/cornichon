package com.github.agourlay.cornichon.steps.wrapped

import com.github.agourlay.cornichon.core._

import scala.concurrent.duration.Duration

// Steps are wrapped/indented with a specific title
case class AttachAsStep(title: String, nestedToRun: Session ⇒ List[Step]) extends LogDecoratorStep {

  override def setTitle(newTitle: String): Step = copy(title = newTitle)

  override def logStackOnNestedError(resultLogStack: List[LogInstruction], depth: Int, executionTime: Duration): List[LogInstruction] =
    FailureLogInstruction(s"$title - Failed", depth, Some(executionTime)) +: resultLogStack :+ failedTitleLog(depth)

  override def logStackOnNestedSuccess(resultLogStack: List[LogInstruction], depth: Int, executionTime: Duration): List[LogInstruction] =
    SuccessLogInstruction(s"$title - Succeeded", depth, Some(executionTime)) +: resultLogStack :+ successTitleLog(depth)
}
