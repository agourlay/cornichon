package com.github.agourlay.cornichon.steps.wrapped

import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.core.Done._
import com.github.agourlay.cornichon.util.Timing._

import monix.execution.Scheduler

import scala.concurrent.duration.{ Duration, FiniteDuration }

case class WithinStep(nested: List[Step], maxDuration: Duration) extends WrapperStep {

  val title = s"Within block with max duration '$maxDuration'"

  override def run(engine: Engine)(initialRunState: RunState)(implicit scheduler: Scheduler) = {

    val initialDepth = initialRunState.depth

    withDuration {
      engine.runSteps(initialRunState.forNestedSteps(nested))
    }.map {
      case ((withinState, res), executionTime) ⇒
        val (fullLogs, xor) = res.fold(
          failedStep ⇒ {
            // Failure of the nested steps have a higher priority
            val fullLogs = failedTitleLog(initialDepth) +: withinState.logs
            (fullLogs, Left(failedStep))
          },
          _ ⇒ {
            val successLogs = successTitleLog(initialDepth) +: withinState.logs
            if (executionTime.gt(maxDuration)) {
              val fullLogs = successLogs :+ FailureLogInstruction("Within block did not complete in time", initialDepth, Some(executionTime))
              // The nested steps were successful but the did not finish in time, the last step is picked as failed step
              val failedStep = FailedStep.fromSingle(nested.last, WithinBlockSucceedAfterMaxDuration(maxDuration, executionTime))
              (fullLogs, Left(failedStep))
            } else {
              val fullLogs = successLogs :+ SuccessLogInstruction("Within block succeeded", initialDepth, Some(executionTime))
              (fullLogs, rightDone)
            }
          }
        )
        (initialRunState.withSession(withinState.session).appendLogs(fullLogs), xor)

    }
  }
}

case class WithinBlockSucceedAfterMaxDuration(maxDuration: Duration, executionTime: FiniteDuration) extends CornichonError {
  val baseErrorMessage = s"Within block succeeded after specified max duration $maxDuration in ${executionTime.toUnit(maxDuration.unit)} ${maxDuration.unit}"
}
