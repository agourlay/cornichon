package com.github.agourlay.cornichon.steps.wrapped

import cats.data.Xor
import cats.data.Xor._

import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.core.Engine._
import com.github.agourlay.cornichon.core.Done._

import scala.annotation.tailrec
import scala.concurrent.ExecutionContext

case class RetryMaxStep(nested: Vector[Step], limit: Int) extends WrapperStep {

  require(limit > 0, "retry max limit must be a positive number")

  val title = s"RetryMax block with limit '$limit'"

  def run(engine: Engine, session: Session, depth: Int)(implicit ec: ExecutionContext) = {

    @tailrec
    def retryMaxSteps(steps: Vector[Step], session: Session, limit: Int, accLogs: Vector[LogInstruction], retriesNumber: Long, depth: Int): (Long, Session, Vector[LogInstruction], Xor[FailedStep, Done]) = {
      val (newSession, logs, stepsResult) = engine.runSteps(steps, session, Vector.empty, depth)
      stepsResult match {
        case Right(done) ⇒
          (retriesNumber, newSession, accLogs ++ logs, rightDone)
        case Left(failedStep) ⇒
          if (limit > 0)
            // In case of success all logs are returned but they are not printed by default.
            retryMaxSteps(steps, session, limit - 1, accLogs ++ logs, retriesNumber + 1, depth)
          else
            // In case of failure only the logs of the last run are shown to avoid giant traces.
            (retriesNumber, newSession, logs, left(failedStep))
      }
    }

    val (repeatRes, executionTime) = withDuration {
      retryMaxSteps(nested, session, limit, Vector.empty, 0, depth + 1)
    }

    val (retries, newSession, logs, report) = repeatRes

    report match {
      case Right(done) ⇒
        val fullLogs = successTitleLog(depth) +: logs :+ SuccessLogInstruction(s"RetryMax block with limit '$limit' succeeded after '$retries' retries", depth, Some(executionTime))
        (newSession, fullLogs, rightDone)
      case Left(failedStep) ⇒
        val fullLogs = failedTitleLog(depth) +: logs :+ FailureLogInstruction(s"RetryMax block with limit '$limit' failed", depth, Some(executionTime))
        val artificialFailedStep = FailedStep(failedStep.step, RetryMaxBlockReachedLimit)
        (newSession, fullLogs, left(artificialFailedStep))
    }
  }
}

