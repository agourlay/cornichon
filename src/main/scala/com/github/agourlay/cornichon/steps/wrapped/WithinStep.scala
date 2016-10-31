package com.github.agourlay.cornichon.steps.wrapped

import java.util.Timer

import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.core.Done._
import com.github.agourlay.cornichon.util.Timing._

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.Duration
import cats.data.Xor._

case class WithinStep(nested: List[Step], maxDuration: Duration) extends WrapperStep {

  val title = s"Within block with max duration '$maxDuration'"

  override def run(engine: Engine)(initialRunState: RunState)(implicit ec: ExecutionContext, timer: Timer) = {

    val initialDepth = initialRunState.depth

    withDuration {
      engine.runSteps(initialRunState.forNestedSteps(nested))
    }.map {
      case (run, executionTime) ⇒

        val (withinState, res) = run
        val (fullLogs, xor) = res match {
          case Right(_) ⇒
            val successLogs = successTitleLog(initialDepth) +: withinState.logs
            if (executionTime.gt(maxDuration)) {
              val fullLogs = successLogs :+ FailureLogInstruction(s"Within block did not complete in time", initialDepth, Some(executionTime))
              // The nested steps were successful but the did not finish in time, the last step is picked as failed step
              val failedStep = FailedStep(nested.last, WithinBlockSucceedAfterMaxDuration)
              (fullLogs, left(failedStep))
            } else {
              val fullLogs = successLogs :+ SuccessLogInstruction(s"Within block succeeded", initialDepth, Some(executionTime))
              (fullLogs, rightDone)
            }
          case Left(failedStep) ⇒
            // Failure of the nested steps have a higher priority
            val fullLogs = failedTitleLog(initialDepth) +: withinState.logs
            (fullLogs, left(failedStep))
        }

        (initialRunState.withSession(withinState.session).appendLogs(fullLogs), xor)

    }
  }
}

case object WithinBlockSucceedAfterMaxDuration extends CornichonError {
  val msg = "within block succeeded after 'maxDuration'"
}
