package com.github.agourlay.cornichon.steps.wrapped

import com.github.agourlay.cornichon.core.{ FailureStepsResult, _ }
import com.github.agourlay.cornichon.core.Engine._

import scala.concurrent.ExecutionContext

// Steps are wrapped/idented with a specific title
case class AttachAsStep(title: String, nested: Vector[Step]) extends WrapperStep {

  def run(engine: Engine, session: Session, depth: Int)(implicit ec: ExecutionContext) = {
    val (res, executionTime) = withDuration {
      engine.runSteps(nested, session, Vector.empty, depth + 1)
    }
    res match {
      case s: SuccessStepsResult ⇒
        val updatedLogs = successTitleLog(depth) +: s.logs :+ SuccessLogInstruction(s"'$title' succeeded", depth, Some(executionTime))
        s.copy(logs = updatedLogs)
      case f: FailureStepsResult ⇒
        f.copy(logs = failedTitleLog(depth) +: f.logs :+ FailureLogInstruction(s"'$title' failed", depth))
    }
  }
}
