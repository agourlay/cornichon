package com.github.agourlay.cornichon.steps.wrapped

import cats.data.StateT
import cats.effect.IO
import cats.syntax.either._
import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.core.Done._

import scala.concurrent.duration.{Duration, FiniteDuration}

case class WithinStep(nested: List[Step], maxDuration: FiniteDuration) extends WrapperStep {

  val title = s"Within block with max duration '$maxDuration'"

  override val stateUpdate: StepState = StateT { runState =>
    val initialDepth = runState.depth
    val nestedContext = runState.nestedContext

    // When the timeout fires, the nested fiber is cancelled; return a failure built from the pre-run state.
    val timeoutFailedResult: IO[(RunState, Either[FailedStep, Done])] = {
      val fs = FailedStep.fromSingle(nested.last, WithinBlockInterruptedAfterMaxDuration(maxDuration))
      IO.pure((nestedContext, fs.asLeft[Done]))
    }

    ScenarioRunner
      .runStepsShortCircuiting(nested, nestedContext)
      .timeoutTo(maxDuration, timeoutFailedResult)
      .timed
      .map { case (executionTime, (withinState, inputRes)) =>
        val (logStack, res) = inputRes match {
          case l @ Left(_) =>
            // Failure of the nested steps have a higher priority
            val fullLogs = withinState.logStack :+ failedTitleLog(initialDepth)
            (fullLogs, l)
          case Right(_) =>
            if (executionTime.gt(maxDuration)) {
              // Rare race: nested completed concurrently with the timeout firing — treat as failure for consistency.
              val wrappedLogStack =
                FailureLogInstruction("Within block did not complete in time", initialDepth, Some(executionTime)) +: withinState.logStack :+ failedTitleLog(initialDepth)
              val artificialFailedStep = FailedStep.fromSingle(nested.last, WithinBlockSucceedAfterMaxDuration(maxDuration, executionTime))
              (wrappedLogStack, Left(artificialFailedStep))
            } else {
              val wrappedLogStack =
                SuccessLogInstruction("Within block succeeded", initialDepth, Some(executionTime)) +: withinState.logStack :+ successTitleLog(initialDepth)
              (wrappedLogStack, rightDone)
            }
        }
        (runState.mergeNested(withinState, logStack), res)
      }
  }

}

case class WithinBlockSucceedAfterMaxDuration(maxDuration: Duration, executionTime: Duration) extends CornichonError {
  lazy val baseErrorMessage = s"Within block succeeded after specified max duration $maxDuration in ${executionTime.toUnit(maxDuration.unit)} ${maxDuration.unit}"
}

case class WithinBlockInterruptedAfterMaxDuration(maxDuration: Duration) extends CornichonError {
  lazy val baseErrorMessage = s"Within block interrupted after specified max duration $maxDuration"
}
