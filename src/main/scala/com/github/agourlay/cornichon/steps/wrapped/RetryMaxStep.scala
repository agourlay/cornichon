package com.github.agourlay.cornichon.steps.wrapped

import cats.data.Xor
import cats.data.Xor._

import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.core.Done._
import com.github.agourlay.cornichon.util.Timing._

import scala.annotation.tailrec
import scala.concurrent.ExecutionContext

case class RetryMaxStep(nested: Vector[Step], limit: Int) extends WrapperStep {

  require(limit > 0, "retry max limit must be a positive number")

  val title = s"RetryMax block with limit '$limit'"

  override def run(engine: Engine, initialRunState: RunState)(implicit ec: ExecutionContext) = {

    @tailrec
    def retryMaxSteps(runState: RunState, limit: Int, retriesNumber: Long): (Long, RunState, Xor[FailedStep, Done]) = {
      val (retriedState, stepsResult) = engine.runSteps(runState.resetLogs)
      stepsResult match {
        case Right(done) ⇒
          val successState = runState.withSession(retriedState.session).appendLogs(retriedState.logs)
          (retriesNumber, successState, rightDone)
        case Left(failedStep) ⇒
          if (limit > 0)
            // In case of success all logs are returned but they are not printed by default.
            retryMaxSteps(runState.appendLogs(retriedState.logs), limit - 1, retriesNumber + 1)
          else
            // In case of failure only the logs of the last run are shown to avoid giant traces.
            (retriesNumber, retriedState, left(failedStep))
      }
    }

    val ((retries, retriedState, report), executionTime) = withDuration {
      val bootstrapRetryState = initialRunState.withSteps(nested).resetLogs.goDeeper
      retryMaxSteps(bootstrapRetryState, limit, 0)
    }

    val depth = initialRunState.depth

    val (fullLogs, xor) = report match {
      case Right(done) ⇒
        val fullLogs = successTitleLog(depth) +: retriedState.logs :+ SuccessLogInstruction(s"RetryMax block with limit '$limit' succeeded after '$retries' retries", depth, Some(executionTime))
        (fullLogs, rightDone)
      case Left(failedStep) ⇒
        val fullLogs = failedTitleLog(depth) +: retriedState.logs :+ FailureLogInstruction(s"RetryMax block with limit '$limit' failed", depth, Some(executionTime))
        val artificialFailedStep = FailedStep(failedStep.step, RetryMaxBlockReachedLimit)
        (fullLogs, left(artificialFailedStep))
    }

    (initialRunState.withSession(retriedState.session).appendLogs(fullLogs), xor)
  }
}

