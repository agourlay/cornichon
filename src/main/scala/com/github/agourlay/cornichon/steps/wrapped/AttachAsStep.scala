package com.github.agourlay.cornichon.steps.wrapped

import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.core.Done._
import com.github.agourlay.cornichon.util.Timing._
import cats.data.Xor._

import scala.concurrent.ExecutionContext

// Steps are wrapped/indented with a specific title
case class AttachAsStep(title: String, nested: Vector[Step]) extends WrapperStep {

  override def setTitle(newTitle: String) = copy(title = newTitle)

  override def run(engine: Engine)(initialRunState: RunState)(implicit ec: ExecutionContext) =
    withDuration {
      val nestedRunState = initialRunState.withSteps(nested).resetLogs.goDeeper
      engine.runSteps(nestedRunState)
    }.map {
      case (run, executionTime) ⇒

        val (attachedRunState, res) = run

        val nestedLogs = attachedRunState.logs
        val initialDepth = initialRunState.depth
        val (fullLogs, xor) = res.fold(
          failedStep ⇒ {
            val failureLogs = failedTitleLog(initialDepth) +: nestedLogs :+ FailureLogInstruction(s"$title - Failed", initialDepth)
            (failureLogs, left(failedStep))
          },
          done ⇒ {
            val successLogs = successTitleLog(initialDepth) +: nestedLogs :+ SuccessLogInstruction(s"$title - Succeeded", initialDepth, Some(executionTime))
            (successLogs, rightDone)
          }
        )
        (initialRunState.withSession(attachedRunState.session).appendLogs(fullLogs), xor)
    }
}
