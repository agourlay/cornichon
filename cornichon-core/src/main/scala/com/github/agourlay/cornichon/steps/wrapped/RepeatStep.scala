package com.github.agourlay.cornichon.steps.wrapped

import cats.data.{ NonEmptyList, StateT }
import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.core.Done._
import com.github.agourlay.cornichon.core.core.StepState
import com.github.agourlay.cornichon.util.Timing._
import monix.eval.Task

case class RepeatStep(nested: List[Step], occurrence: Int, indiceName: Option[String]) extends WrapperStep {

  require(occurrence > 0, "repeat block must contain a positive number of occurrence")

  val title = s"Repeat block with occurrence '$occurrence'"

  override val stateUpdate: StepState = StateT { runState ⇒

    def repeatSuccessSteps(retriesNumber: Int, runState: RunState): Task[(Int, RunState, Either[FailedStep, Done])] = {
      // reset logs at each loop to have the possibility to not aggregate in failure case
      val rs = runState.resetLogStack
      val runStateWithIndex = indiceName.fold(rs)(in ⇒ rs.addToSession(in, (retriesNumber + 1).toString))
      ScenarioRunner.runStepsShortCircuiting(nested, runStateWithIndex).flatMap {
        case (onceMoreRunState, stepResult) ⇒
          stepResult.fold(
            failed ⇒ {
              // In case of failure only the logs of the last run are shown to avoid giant traces.
              Task.now((retriesNumber, onceMoreRunState, Left(failed)))
            },
            _ ⇒ {
              val successState = runState.withSession(onceMoreRunState.session).recordLogStack(onceMoreRunState.logStack)
              // only show last successful run to avoid giant traces.
              if (retriesNumber == occurrence - 1) Task.now((retriesNumber, successState, rightDone))
              else repeatSuccessSteps(retriesNumber + 1, runState.withSession(onceMoreRunState.session))
            }
          )
      }
    }

    withDuration {
      repeatSuccessSteps(0, runState.nestedContext)
    }.map {
      case (run, executionTime) ⇒
        val (retries, repeatedState, report) = run
        val depth = runState.depth
        val (logStack, res) = report.fold(
          failedStep ⇒ {
            val wrappedLogStack = FailureLogInstruction(s"Repeat block with occurrence '$occurrence' failed after '$retries' occurrence", depth, Some(executionTime)) +: repeatedState.logStack :+ failedTitleLog(depth)
            val artificialFailedStep = FailedStep.fromSingle(failedStep.step, RepeatBlockContainFailedSteps(retries, failedStep.errors))
            (wrappedLogStack, Left(artificialFailedStep))
          },
          _ ⇒ {
            val wrappedLockStack = SuccessLogInstruction(s"Repeat block with occurrence '$occurrence' succeeded", depth, Some(executionTime)) +: repeatedState.logStack :+ successTitleLog(depth)
            (wrappedLockStack, rightDone)
          }
        )
        (runState.mergeNested(repeatedState, logStack), res)
    }
  }
}

case class RepeatBlockContainFailedSteps(failedOccurrence: Int, errors: NonEmptyList[CornichonError]) extends CornichonError {
  lazy val baseErrorMessage = s"Repeat block failed at occurrence $failedOccurrence"
  override val causedBy = errors.toList
}
