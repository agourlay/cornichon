package com.github.agourlay.cornichon.steps.wrapped

import cats.data.{ NonEmptyList, StateT }
import cats.effect.IO
import cats.syntax.either._
import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.core.Done._

import scala.concurrent.duration.{ Duration, FiniteDuration }

case class EventuallyStep(nested: List[Step], conf: EventuallyConf) extends WrapperStep {

  val title = s"Eventually block with maxDuration = ${conf.maxTime} and interval = ${conf.interval}"

  override val stateUpdate: StepState = StateT { runState =>

    def retryEventuallySteps(runState: RunState, conf: EventuallyConf, retriesNumber: Long, lastErrorState: Option[RunState]): IO[(Long, RunState, Either[FailedStep, Done])] = {
      ScenarioRunner.runStepsShortCircuiting(nested, runState)
        .delayBy(if (retriesNumber == 0) Duration.Zero else conf.interval)
        .timed
        .flatMap {
          case (executionTime, (newRunState, res)) =>
            val remainingTime = conf.maxTime - executionTime
            res match {
              case Left(failedStep) =>
                // discard inner session and logs
                val updatedRunState = runState.registerCleanupSteps(newRunState.cleanupSteps)
                // Check that it could go through another loop after the interval
                if ((remainingTime - conf.interval).gt(Duration.Zero))
                  retryEventuallySteps(updatedRunState, conf.consume(executionTime), retriesNumber + 1, Some(newRunState))
                else {
                  // no time for another loop
                  // return last state fully because intermediate states were discarded
                  IO.pure((retriesNumber, newRunState, failedStep.asLeft))
                }

              case Right(_) =>
                if (remainingTime.gt(Duration.Zero)) {
                  lastErrorState match {
                    case Some(prevErrorState) =>
                      // only show the last error
                      val mergedState = prevErrorState.mergeNested(newRunState)
                      IO.pure((retriesNumber, mergedState, Done.rightDone))
                    case _ =>
                      // return last state fully
                      IO.pure((retriesNumber, newRunState, rightDone))
                  }
                } else {
                  // Run was a success but the time is up.
                  val failedStep = FailedStep.fromSingle(nested.last, EventuallyBlockSucceedAfterMaxDuration)
                  IO.pure((retriesNumber, newRunState, failedStep.asLeft))
                }
            }
        }
    }

    def timeoutFailedResult: IO[(Long, RunState, Either[FailedStep, Done])] = {
      val fs = FailedStep(this, NonEmptyList.of(EventuallyBlockMaxInactivity))
      IO.delay((0, runState.nestedContext, fs.asLeft[Done]))
    }

    retryEventuallySteps(runState.nestedContext, conf, 0, None)
      .timeoutTo(duration = conf.maxTime * 2, fallback = timeoutFailedResult) // make sure that the inner block does not run forever
      .timed
      .map {
        case (executionTime, (retries, retriedRunState, report)) =>
          val initialDepth = runState.depth
          val wrappedLogStack = report match {
            case Left(_) =>
              FailureLogInstruction(s"Eventually block did not complete in time after having being tried '${retries + 1}' times", initialDepth, Some(executionTime)) +: retriedRunState.logStack :+ failedTitleLog(initialDepth)
            case _ =>
              SuccessLogInstruction(s"Eventually block succeeded after '$retries' retries", initialDepth, Some(executionTime)) +: retriedRunState.logStack :+ successTitleLog(initialDepth)
          }
          (runState.mergeNested(retriedRunState, wrappedLogStack), report)
      }
  }
}

case class EventuallyConf(maxTime: FiniteDuration, interval: FiniteDuration) {
  def consume(burnt: FiniteDuration): EventuallyConf = {
    val rest = maxTime - burnt
    val newMax = if (rest.lteq(Duration.Zero)) Duration.Zero else rest
    copy(maxTime = newMax)
  }
}

object EventuallyConf {
  val empty: EventuallyConf = EventuallyConf(Duration.Zero, Duration.Zero)
}

case object EventuallyBlockSucceedAfterMaxDuration extends CornichonError {
  lazy val baseErrorMessage = "Eventually block succeeded after 'maxDuration'"
}

case object EventuallyBlockMaxInactivity extends CornichonError {
  lazy val baseErrorMessage = "Eventually block is interrupted due to a long period of inactivity"
}
