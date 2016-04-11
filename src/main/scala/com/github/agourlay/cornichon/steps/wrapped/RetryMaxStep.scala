package com.github.agourlay.cornichon.steps.wrapped

import com.github.agourlay.cornichon.core._

import scala.annotation.tailrec
import scala.concurrent.ExecutionContext

case class RetryMaxStep(nested: Vector[Step], limit: Int) extends WrapperStep {

  require(limit > 0, "rety max limit must be a positive number")

  val title = s"RetryMax block with limit '$limit'"

  def run(engine: Engine, session: Session, depth: Int)(implicit ec: ExecutionContext) = {

    @tailrec
    def retryMaxSteps(steps: Vector[Step], session: Session, limit: Int, accLogs: Vector[LogInstruction], depth: Int): StepsReport = {
      engine.runSteps(steps, session, Vector.empty, depth) match {
        case s @ SuccessRunSteps(sSession, sLogs) ⇒ s.copy(logs = accLogs ++ sLogs)
        case f @ FailedRunSteps(_, _, eLogs, fSession) ⇒
          if (limit > 0)
            retryMaxSteps(steps, session, limit - 1, accLogs ++ eLogs, depth)
          else
            f.copy(logs = accLogs ++ eLogs)
      }
    }

    val titleLog = DefaultLogInstruction(title, depth)
    val (repeatRes, executionTime) = engine.withDuration {
      retryMaxSteps(nested, session, limit, Vector.empty, depth + 1)
    }
    if (repeatRes.isSuccess) {
      val fullLogs = (titleLog +: repeatRes.logs) :+ SuccessLogInstruction(s"RetryMax block with limit $limit succeeded", depth, Some(executionTime))
      SuccessRunSteps(repeatRes.session, fullLogs)
    } else {
      val fullLogs = (titleLog +: repeatRes.logs) :+ FailureLogInstruction(s"RetryMax block with limit $limit failed", depth, Some(executionTime))
      FailedRunSteps(nested.last, RetryMaxBlockReachedLimit, fullLogs, repeatRes.session)
    }
  }
}

