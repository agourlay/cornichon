package com.github.agourlay.cornichon.steps.wrapped

import cats.data.{NonEmptyList, StateT}
import cats.effect.IO
import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.core.Done._

case class RepeatStep(nested: List[Step], occurrence: Int, indexName: Option[String]) extends WrapperStep {

  require(occurrence > 0, "repeat block must contain a positive number of occurrence")

  val title = s"Repeat block with occurrence '$occurrence'"

  override val stateUpdate: StepState = StateT { runState =>
    def repeatSuccessSteps(retriesNumber: Int, runState: RunState): IO[(Int, RunState, Either[FailedStep, Done])] = {
      // reset logs and cleanup steps at each loop to have the possibility to not aggregate in failure case
      val rs = runState.iterationContext
      val runStateWithIndex = indexName.fold(rs)(in => rs.addToSession(in, (retriesNumber + 1).toString))
      ScenarioRunner.runStepsShortCircuiting(nested, runStateWithIndex).flatMap { case (onceMoreRunState, stepResult) =>
        stepResult.fold(
          failed =>
            // In case of failure only the logs of the last run are shown to avoid giant traces.
            IO.pure((retriesNumber, onceMoreRunState.withPreviousCleanupSteps(runState.cleanupSteps), Left(failed))),
          _ =>
            // only show last successful run to avoid giant traces.
            if (retriesNumber == occurrence - 1) {
              val successState = runState.copy(
                session = onceMoreRunState.session,
                logStack = onceMoreRunState.logStack ++ runState.logStack,
                cleanupSteps = onceMoreRunState.cleanupSteps ::: runState.cleanupSteps
              )
              IO.pure((retriesNumber, successState, rightDone))
            } else {
              // single copy carrying the session and cleanup steps forward, logs of this occurrence are dropped
              val nextState = runState.copy(session = onceMoreRunState.session, cleanupSteps = onceMoreRunState.cleanupSteps ::: runState.cleanupSteps)
              repeatSuccessSteps(retriesNumber + 1, nextState)
            }
        )
      }
    }

    repeatSuccessSteps(0, runState.nestedContext).timed
      .map { case (executionTime, run) =>
        val (retries, repeatedState, report) = run
        val depth = runState.depth
        val (logStack, res) = report.fold(
          failedStep => {
            val wrappedLogStack = FailureLogInstruction(
              s"Repeat block with occurrence '$occurrence' failed after '$retries' occurrence",
              depth,
              Some(executionTime)
            ) +: repeatedState.logStack :+ failedTitleLog(depth)
            // `retries` counts completed occurrences, so the failing one is the next - which is also
            // what the `indexName` session key held during that run
            val artificialFailedStep = FailedStep.fromSingle(failedStep.step, RepeatBlockContainFailedSteps(retries + 1, failedStep.errors))
            (wrappedLogStack, Left(artificialFailedStep))
          },
          _ => {
            val wrappedLockStack =
              SuccessLogInstruction(s"Repeat block with occurrence '$occurrence' succeeded", depth, Some(executionTime)) +: repeatedState.logStack :+ successTitleLog(
                depth
              )
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
