package com.github.agourlay.cornichon.steps.wrapped

import cats.data.StateT
import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.core.Done._
import com.github.agourlay.cornichon.core.core.StepState
import com.github.agourlay.cornichon.util.Timing._

import scala.concurrent.duration.Duration

case class WithinStep(nested: List[Step], maxDuration: Duration) extends WrapperStep {

  val title = s"Within block with max duration '$maxDuration'"

  override def onEngine(engine: Engine): StepState = StateT { initialRunState ⇒

    val initialDepth = initialRunState.depth

    withDuration {
      engine.runStepsShortCircuiting(nested, initialRunState.nestedContext)
    }.map {
      case ((withinState, inputRes), executionTime) ⇒
        val (logStack, res) = inputRes match {
          case l @ Left(_) ⇒
            // Failure of the nested steps have a higher priority
            val fullLogs = withinState.logStack :+ failedTitleLog(initialDepth)
            (fullLogs, l)

          case Right(_) ⇒
            if (executionTime.gt(maxDuration)) {
              val wrappedLogStack = FailureLogInstruction("Within block did not complete in time", initialDepth, Some(executionTime)) +: withinState.logStack :+ successTitleLog(initialDepth)
              // The nested steps were successful but the did not finish in time, the last step is picked as failed step
              val artificialFailedStep = FailedStep.fromSingle(nested.last, WithinBlockSucceedAfterMaxDuration(maxDuration, executionTime))
              (wrappedLogStack, Left(artificialFailedStep))
            } else {
              val wrappedLogStack = SuccessLogInstruction("Within block succeeded", initialDepth, Some(executionTime)) +: withinState.logStack :+ successTitleLog(initialDepth)
              (wrappedLogStack, rightDone)
            }
        }
        (initialRunState.mergeNested(withinState, logStack), res)

    }
  }
}

case class WithinBlockSucceedAfterMaxDuration(maxDuration: Duration, executionTime: Duration) extends CornichonError {
  val baseErrorMessage = s"Within block succeeded after specified max duration $maxDuration in ${executionTime.toUnit(maxDuration.unit)} ${maxDuration.unit}"
}
