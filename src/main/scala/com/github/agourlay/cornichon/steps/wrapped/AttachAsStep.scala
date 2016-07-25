package com.github.agourlay.cornichon.steps.wrapped

import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.core.Engine._
import com.github.agourlay.cornichon.core.Done._

import cats.data.Xor._

import scala.concurrent.ExecutionContext

// Steps are wrapped/idented with a specific title
case class AttachAsStep(title: String, nested: Vector[Step]) extends WrapperStep {

  def run(engine: Engine, session: Session, depth: Int)(implicit ec: ExecutionContext) = {
    val ((newSession, runLogs, res), executionTime) = withDuration {
      engine.runSteps(nested, session, Vector.empty, depth + 1)
    }
    res.fold(
      failedStep ⇒ {
        val failureLogs = failedTitleLog(depth) +: runLogs :+ FailureLogInstruction(s"'$title' failed", depth)
        (newSession, failureLogs, left(failedStep))
      },
      done ⇒ {
        val updatedLogs = successTitleLog(depth) +: runLogs :+ SuccessLogInstruction(s"'$title' succeeded", depth, Some(executionTime))
        (newSession, updatedLogs, rightDone)
      }
    )
  }
}
