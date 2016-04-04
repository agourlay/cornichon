package com.github.agourlay.cornichon.steps.wrapped

import com.github.agourlay.cornichon.core._

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.Duration

case class WithinStep(nested: Vector[Step], maxDuration: Duration) extends WrapperStep {
  val title = s"Within block with max duration '$maxDuration'"

  def run(engine: Engine, nextSteps: Vector[Step], session: Session, logs: Vector[LogInstruction], depth: Int)(implicit ec: ExecutionContext) = {
    val updatedLogs = logs :+ DefaultLogInstruction(title, depth)
    val (res, executionTime) = engine.withDuration {
      engine.runSteps(nested, session, Vector.empty, depth + 1)
    }

    res match {
      case s @ SuccessRunSteps(sSession, sLogs) ⇒
        val successLogs = updatedLogs ++ sLogs
        if (executionTime.gt(maxDuration)) {
          val fullLogs = (successLogs :+ FailureLogInstruction(s"Within block did not complete in time", depth, Some(executionTime))) ++ engine.logNonExecutedStep(nextSteps.tail, depth)
          engine.buildFailedRunSteps(nested.last, nextSteps, WithinBlockSucceedAfterMaxDuration, fullLogs, sSession)
        } else {
          val fullLogs = successLogs :+ SuccessLogInstruction(s"Within block succeeded", depth, Some(executionTime))
          engine.runSteps(nextSteps, sSession, fullLogs, depth)
        }
      case f @ FailedRunSteps(_, _, eLogs, fSession) ⇒
        // Failure of the nested steps have a higher priority
        val fullLogs = updatedLogs ++ eLogs ++ engine.logNonExecutedStep(nextSteps, depth)
        f.copy(logs = fullLogs, session = fSession)
    }
  }
}
