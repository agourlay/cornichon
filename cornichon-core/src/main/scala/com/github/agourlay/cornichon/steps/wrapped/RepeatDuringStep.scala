package com.github.agourlay.cornichon.steps.wrapped

import java.util.concurrent.TimeUnit

import cats.data.{ NonEmptyList, StateT }
import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.core.Done._
import com.github.agourlay.cornichon.core.core.StepState
import com.github.agourlay.cornichon.util.Timing._
import monix.eval.Task

import scala.concurrent.duration.FiniteDuration

case class RepeatDuringStep(nested: List[Step], duration: FiniteDuration) extends WrapperStep {

  val title = s"Repeat block during '$duration'"

  override val stateUpdate: StepState = StateT { runState ⇒

    val initialDepth = runState.depth

    def repeatStepsDuring(runState: RunState, duration: FiniteDuration, retriesNumber: Long): Task[(Long, RunState, Either[FailedStep, Done])] = {
      withDuration {
        // reset logs at each loop to have the possibility to not aggregate in failure case
        runState.engine.runStepsShortCircuiting(nested, runState.resetLogStack)
      }.flatMap {
        case (run, executionTime) ⇒
          val (repeatedOnceMore, res) = run
          val remainingTime = duration - executionTime
          res.fold(
            failedStep ⇒ {
              // In case of failure only the logs of the last run are shown to avoid giant traces.
              Task.now((retriesNumber, repeatedOnceMore, Left(failedStep)))
            },
            _ ⇒ {
              val successState = runState.mergeNested(repeatedOnceMore)
              if (remainingTime.gt(FiniteDuration(0, TimeUnit.MILLISECONDS)))
                repeatStepsDuring(successState, remainingTime, retriesNumber + 1)
              else
                // In case of success all logs are returned but they are not printed by default.
                Task.now((retriesNumber, successState, rightDone))
            }
          )
      }
    }

    withDuration {
      repeatStepsDuring(runState.nestedContext, duration, 0)
    }.map {
      case (run, executionTime) ⇒
        val (retries, repeatedRunState, report) = run
        val (logStack, res) = report.fold(
          failedStep ⇒ {
            val wrappedLogStack = FailureLogInstruction(s"Repeat block during '$duration' failed after being retried '$retries' times", initialDepth, Some(executionTime)) +: repeatedRunState.logStack :+ failedTitleLog(initialDepth)
            val artificialFailedStep = FailedStep.fromSingle(failedStep.step, RepeatDuringBlockContainFailedSteps(duration, failedStep.errors))
            (wrappedLogStack, Left(artificialFailedStep))
          },
          _ ⇒ {
            val wrappedLogStack = SuccessLogInstruction(s"Repeat block during '$duration' succeeded after '$retries' retries", initialDepth, Some(executionTime)) +: repeatedRunState.logStack :+ successTitleLog(initialDepth)
            (wrappedLogStack, rightDone)
          }
        )
        (runState.mergeNested(repeatedRunState, logStack), res)
    }
  }
}

case class RepeatDuringBlockContainFailedSteps(duration: FiniteDuration, errors: NonEmptyList[CornichonError]) extends CornichonError {
  val baseErrorMessage = s"RepeatDuring block failed before '$duration'"
  override val causedBy = errors.toList
}
