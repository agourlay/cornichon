package com.github.agourlay.cornichon.steps.wrapped

import cats.data.{ NonEmptyList, StateT }
import cats.effect.IO
import cats.syntax.either._
import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.core.Done._

import scala.concurrent.duration.{ Duration, FiniteDuration }

case class EventuallyStep(nested: List[Step], conf: EventuallyConf, oscillationAllowed: Boolean, discardStateOnError: Boolean) extends WrapperStep {

  val title = s"Eventually block with maxDuration = ${conf.maxTime} and interval = ${conf.interval}"

  override val stateUpdate: StepState = StateT { runState =>

    def retryEventuallySteps(runState: RunState, conf: EventuallyConf, retriesNumber: Long, knownErrors: List[FailedStep]): IO[(Long, Int, RunState, Either[FailedStep, Done])] = {

      def distinctErrorsWith(fs: FailedStep): Int =
        (fs :: knownErrors).toSet.size

      def distinctErrors: Int =
        knownErrors.toSet.size

      // Error already seen in the past with another error in between
      def oscillationDetectedForFailedStep(fs: FailedStep): Boolean =
        knownErrors.nonEmpty && fs != knownErrors.head && knownErrors.tail.contains(fs)

      ScenarioRunner.runStepsShortCircuiting(nested, runState)
        .delayBy(if (retriesNumber == 0) Duration.Zero else conf.interval)
        .timed
        .flatMap {
          case (executionTime, (newRunState, res)) =>
            val remainingTime = conf.maxTime - executionTime
            res match {
              case Left(failedStep) =>
                val oscillationDetected = !oscillationAllowed && oscillationDetectedForFailedStep(failedStep)
                // early exit for oscillation detection
                if (oscillationDetected) {
                  val fsOscillation = FailedStep.fromSingle(this, EventuallyBlockOscillationDetected(failedStep))
                  return IO.pure((retriesNumber, distinctErrors, newRunState, fsOscillation.asLeft))
                }

                // control precisely which state is propagated
                val updatedRunState = {
                  if (discardStateOnError) {
                    // discard inner session and logs
                    runState.registerCleanupSteps(newRunState.cleanupSteps)
                  } else if (knownErrors.contains(failedStep)) {
                    // known error only propagate cleanup steps and session as we know the logs already
                    runState.registerCleanupSteps(newRunState.cleanupSteps).withSession(newRunState.session)
                  } else {
                    // new error - return the whole inner state
                    newRunState
                  }
                }

                // Check that it could go through another loop after the interval
                if ((remainingTime - conf.interval).gt(Duration.Zero))
                  retryEventuallySteps(updatedRunState, conf.consume(executionTime), retriesNumber + 1, failedStep :: knownErrors)
                else {
                  IO.pure((retriesNumber, distinctErrorsWith(failedStep), updatedRunState, failedStep.asLeft))
                }

              case Right(_) =>
                if (remainingTime.gt(Duration.Zero)) {
                  // In case of success all logs are returned but they are not printed by default.
                  IO.pure((retriesNumber, distinctErrors, newRunState, rightDone))
                } else {
                  // Run was a success but the time is up.
                  val failedStep = FailedStep.fromSingle(nested.last, EventuallyBlockSucceedAfterMaxDuration)
                  IO.pure((retriesNumber, distinctErrors, newRunState, failedStep.asLeft))
                }
            }
        }
    }

    def timeoutFailedResult: IO[(Long, Int, RunState, Either[FailedStep, Done])] = {
      val fs = FailedStep(this, NonEmptyList.of(EventuallyBlockMaxInactivity))
      IO.delay((0, 0, runState.nestedContext, fs.asLeft[Done]))
    }

    retryEventuallySteps(runState.nestedContext, conf, 0, Nil)
      .timeoutTo(duration = conf.maxTime * 2, fallback = timeoutFailedResult) // make sure that the inner block does not run forever
      .timed
      .map {
        case (executionTime, (retries, distinctErrors, retriedRunState, report)) =>
          val initialDepth = runState.depth
          val wrappedLogStack = report match {
            case Left(_) =>
              FailureLogInstruction(s"Eventually block did not complete in time after having being tried '${retries + 1}' times with '$distinctErrors' distinct errors", initialDepth, Some(executionTime)) +: retriedRunState.logStack :+ failedTitleLog(initialDepth)
            case _ =>
              SuccessLogInstruction(s"Eventually block succeeded after '$retries' retries with '$distinctErrors' distinct errors", initialDepth, Some(executionTime)) +: retriedRunState.logStack :+ successTitleLog(initialDepth)
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
  val empty = EventuallyConf(Duration.Zero, Duration.Zero)
}

case object EventuallyBlockSucceedAfterMaxDuration extends CornichonError {
  lazy val baseErrorMessage = "Eventually block succeeded after 'maxDuration'"
}

case object EventuallyBlockMaxInactivity extends CornichonError {
  lazy val baseErrorMessage = "Eventually block is interrupted due to a long period of inactivity"
}

case class EventuallyBlockOscillationDetected(failedStep: FailedStep) extends CornichonError {
  lazy val baseErrorMessage = s"Eventually block failed because it detected an oscillation of errors\n${failedStep.messageForFailedStep}"
}