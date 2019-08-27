package com.github.agourlay.cornichon.steps.wrapped

import cats.data.{ NonEmptyList, StateT }
import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.core.Done._
import monix.eval.Task

case class RetryMaxStep(nested: List[Step], limit: Int) extends WrapperStep {

  require(limit > 0, "retry max limit must be a positive number")

  val title = s"RetryMax block with limit '$limit'"

  override val stateUpdate: StepState = StateT { runState ⇒

    def retryMaxSteps(runState: RunState, limit: Int, retriesNumber: Long): Task[(Long, RunState, Either[FailedStep, Done])] =
      ScenarioRunner.runStepsShortCircuiting(nested, runState.resetLogStack).flatMap {
        case (retriedState, Left(_)) if limit > 0 ⇒
          // In case of success all logs are returned but they are not printed by default.
          retryMaxSteps(runState.recordLogStack(retriedState.logStack), limit - 1, retriesNumber + 1)

        case (retriedState, l @ Left(_)) ⇒
          // In case of failure only the logs of the last run are shown to avoid giant traces.
          Task.now((retriesNumber, retriedState, l))

        case (retriedState, _) ⇒
          val successState = runState.withSession(retriedState.session).recordLogStack(retriedState.logStack)
          Task.now((retriesNumber, successState, rightDone))

      }

    retryMaxSteps(runState.nestedContext, limit, 0)
      .timed
      .map {
        case (executionTime, run) ⇒
          val (retries, retriedState, report) = run
          val depth = runState.depth
          val (logStack, res) = report.fold(
            failedStep ⇒ {
              val wrappedLogStack = FailureLogInstruction(s"RetryMax block with limit '$limit' failed", depth, Some(executionTime)) +: retriedState.logStack :+ failedTitleLog(depth)
              val artificialFailedStep = FailedStep.fromSingle(failedStep.step, RetryMaxBlockReachedLimit(limit, failedStep.errors))
              (wrappedLogStack, Left(artificialFailedStep))
            },
            _ ⇒ {
              val wrappedLogStack = SuccessLogInstruction(s"RetryMax block with limit '$limit' succeeded after '$retries' retries", depth, Some(executionTime)) +: retriedState.logStack :+ successTitleLog(depth)
              (wrappedLogStack, rightDone)
            }
          )
          (runState.mergeNested(retriedState, logStack), res)
      }
  }
}

case class RetryMaxBlockReachedLimit(limit: Int, errors: NonEmptyList[CornichonError]) extends CornichonError {
  lazy val baseErrorMessage = s"Retry max block failed '$limit' times"
  override val causedBy = errors.toList
}
