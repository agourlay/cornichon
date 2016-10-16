package com.github.agourlay.cornichon.steps.wrapped

import java.util.Timer

import cats.data.Xor
import cats.data.Xor._
import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.core.Done._
import com.github.agourlay.cornichon.util.Timing._

import scala.concurrent.{ ExecutionContext, Future }

case class RetryMaxStep(nested: List[Step], limit: Int) extends WrapperStep {

  require(limit > 0, "retry max limit must be a positive number")

  val title = s"RetryMax block with limit '$limit'"

  override def run(engine: Engine)(initialRunState: RunState)(implicit ec: ExecutionContext, timer: Timer) = {

    def retryMaxSteps(runState: RunState, limit: Int, retriesNumber: Long): Future[(Long, RunState, Xor[FailedStep, Done])] =
      engine.runSteps(runState.resetLogs).flatMap {
        case (retriedState, stepsResult) ⇒
          stepsResult match {
            case Right(_) ⇒
              val successState = runState.withSession(retriedState.session).appendLogs(retriedState.logs)
              Future.successful(retriesNumber, successState, rightDone)
            case Left(failedStep) ⇒
              if (limit > 0)
                // In case of success all logs are returned but they are not printed by default.
                retryMaxSteps(runState.appendLogs(retriedState.logs), limit - 1, retriesNumber + 1)
              else
                // In case of failure only the logs of the last run are shown to avoid giant traces.
                Future.successful(retriesNumber, retriedState, left(failedStep))
          }
      }

    withDuration {
      val bootstrapRetryState = initialRunState.withSteps(nested).resetLogs.goDeeper
      retryMaxSteps(bootstrapRetryState, limit, 0)
    }.map {
      case (run, executionTime) ⇒
        val (retries, retriedState, report) = run
        val depth = initialRunState.depth

        val (fullLogs, xor) = report match {
          case Right(_) ⇒
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
}

case object RetryMaxBlockReachedLimit extends CornichonError {
  val msg = "retry max block reached the limit"
}
