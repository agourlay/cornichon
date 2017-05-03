package com.github.agourlay.cornichon.steps.wrapped

import cats.data.NonEmptyList

import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.core.Done._
import com.github.agourlay.cornichon.util.Timing._

import monix.execution.Scheduler

import scala.concurrent.Future

case class RetryMaxStep(nested: List[Step], limit: Int) extends WrapperStep {

  require(limit > 0, "retry max limit must be a positive number")

  val title = s"RetryMax block with limit '$limit'"

  override def run(engine: Engine)(initialRunState: RunState)(implicit scheduler: Scheduler) = {

    def retryMaxSteps(runState: RunState, limit: Int, retriesNumber: Long): Future[(Long, RunState, Either[FailedStep, Done])] =
      engine.runSteps(runState.resetLogs).flatMap {
        case (retriedState, stepsResult) ⇒
          stepsResult.fold(
            failedStep ⇒
              if (limit > 0)
                // In case of success all logs are returned but they are not printed by default.
                retryMaxSteps(runState.appendLogs(retriedState.logs), limit - 1, retriesNumber + 1)
              else
                // In case of failure only the logs of the last run are shown to avoid giant traces.
                Future.successful(retriesNumber, retriedState, Left(failedStep)),
            _ ⇒ {
              val successState = runState.withSession(retriedState.session).appendLogs(retriedState.logs)
              Future.successful(retriesNumber, successState, rightDone)
            }
          )
      }

    withDuration {
      val bootstrapRetryState = initialRunState.forNestedSteps(nested)
      retryMaxSteps(bootstrapRetryState, limit, 0)
    }.map {
      case (run, executionTime) ⇒
        val (retries, retriedState, report) = run
        val depth = initialRunState.depth
        val (fullLogs, xor) = report.fold(
          failedStep ⇒ {
            val fullLogs = failedTitleLog(depth) +: retriedState.logs :+ FailureLogInstruction(s"RetryMax block with limit '$limit' failed", depth, Some(executionTime))
            val artificialFailedStep = FailedStep.fromSingle(failedStep.step, RetryMaxBlockReachedLimit(limit, failedStep.errors))
            (fullLogs, Left(artificialFailedStep))
          },
          _ ⇒ {
            val fullLogs = successTitleLog(depth) +: retriedState.logs :+ SuccessLogInstruction(s"RetryMax block with limit '$limit' succeeded after '$retries' retries", depth, Some(executionTime))
            (fullLogs, rightDone)
          }
        )
        (initialRunState.withSession(retriedState.session).appendLogs(fullLogs), xor)
    }
  }
}

case class RetryMaxBlockReachedLimit(limit: Int, errors: NonEmptyList[CornichonError]) extends CornichonError {
  val baseErrorMessage = s"Retry max block failed '$limit' times"
  override val causedBy = Some(errors)
}
