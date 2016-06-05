package com.github.agourlay.cornichon.steps.wrapped

import com.github.agourlay.cornichon.core._

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.Duration

case class WithinStep(nested: Vector[Step], maxDuration: Duration) extends WrapperStep {
  val title = s"Within block with max duration '$maxDuration'"

  def run(engine: Engine, session: Session, depth: Int)(implicit ec: ExecutionContext) = {

    val (res, executionTime) = engine.withDuration {
      engine.runSteps(nested, session, Vector.empty, depth + 1)
    }

    res match {
      case s @ SuccessStepsResult(sSession, sLogs) ⇒
        val successLogs = successTitleLog(depth) +: sLogs
        if (executionTime.gt(maxDuration)) {
          val fullLogs = successLogs :+ FailureLogInstruction(s"Within block did not complete in time", depth, Some(executionTime))
          // The nested steps were successfull but the did not finish in time, the last step is picked as failed step
          val failedStep = FailedStep(nested.last, WithinBlockSucceedAfterMaxDuration)
          FailureStepsResult(failedStep, fullLogs, sSession)
        } else {
          val fullLogs = successLogs :+ SuccessLogInstruction(s"Within block succeeded", depth, Some(executionTime))
          s.copy(logs = fullLogs)
        }
      case f @ FailureStepsResult(_, eLogs, fSession) ⇒
        // Failure of the nested steps have a higher priority
        val fullLogs = failedTitleLog(depth) +: eLogs
        f.copy(logs = fullLogs, session = fSession)
    }
  }
}
