package com.github.agourlay.cornichon.steps.wrapped

import cats.data.NonEmptyList
import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.core.Done._
import com.github.agourlay.cornichon.util.Timing._
import cats.syntax.either._
import com.github.agourlay.cornichon.core.core.StepResult
import monix.eval.Task

import scala.concurrent.duration.{ Duration, FiniteDuration }

case class EventuallyStep(nested: List[Step], conf: EventuallyConf, oscillationAllowed: Boolean) extends WrapperStep {
  val title = s"Eventually block with maxDuration = ${conf.maxTime} and interval = ${conf.interval}"

  override def run(engine: Engine)(initialRunState: RunState): StepResult = {

    def retryEventuallySteps(runState: RunState, conf: EventuallyConf, retriesNumber: Long, knownErrors: List[FailedStep]): Task[(Long, RunState, Either[FailedStep, Done])] = {

      // Propagate the logs only if it is a new error OR not the case of an oscillation we want to report
      def handleFailureLogsPropagation(failedStep: FailedStep, previousRs: RunState, nextRunState: RunState, oscillationDetected: Boolean): RunState =
        if (oscillationDetected)
          nextRunState
        else if (knownErrors.contains(failedStep))
          previousRs
        else {
          val logsToAdd = nextRunState.logs.diff(previousRs.logs)
          previousRs.appendLogs(logsToAdd)
        }

      // Error already seen in the past with another error in between
      def oscillationDetectedForFailedStep(fs: FailedStep): Boolean =
        knownErrors.nonEmpty && fs != knownErrors.head && knownErrors.tail.contains(fs)

      withDuration {
        val nestedTask = engine.runSteps(nested, runState)
        if (retriesNumber == 0) nestedTask else nestedTask.delayExecution(conf.interval)
      }.flatMap {
        case ((newRunState, res), executionTime) ⇒
          val remainingTime = conf.maxTime - executionTime
          res.fold(
            failedStep ⇒ {
              val oscillationDetected = !oscillationAllowed && oscillationDetectedForFailedStep(failedStep)
              // Propagate cleanup steps and session
              val mergedState = runState.prependCleanupStepsFrom(newRunState).withSession(newRunState.session)
              val propagatedState = handleFailureLogsPropagation(failedStep, mergedState, newRunState, oscillationDetected)

              if (oscillationDetected) {
                val fsOscillation = FailedStep.fromSingle(this, EventuallyBlockOscillationDetected(failedStep))
                Task.now((retriesNumber, propagatedState, Left(fsOscillation)))
              } else if ((remainingTime - conf.interval).gt(Duration.Zero)) // Check that it could go through another loop after the interval
                retryEventuallySteps(propagatedState, conf.consume(executionTime), retriesNumber + 1, failedStep :: knownErrors)
              else
                Task.now((retriesNumber, propagatedState, Left(failedStep)))
            },
            _ ⇒ {
              val state = runState.mergeNested(newRunState)
              if (remainingTime.gt(Duration.Zero)) {
                // In case of success all logs are returned but they are not printed by default.
                Task.now((retriesNumber, state, rightDone))
              } else {
                // Run was a success but the time is up.
                val failedStep = FailedStep.fromSingle(nested.last, EventuallyBlockSucceedAfterMaxDuration)
                Task.now((retriesNumber, state, Left(failedStep)))
              }
            }
          )
      }
    }

    def timeoutFailedResult: Task[(Long, RunState, Either[FailedStep, Done])] = {
      val fs = FailedStep(this, NonEmptyList.of(EventuallyBlockMaxInactivity))
      Task.delay((0, initialRunState.nestedContext, fs.asLeft[Done]))
    }

    withDuration {
      val eventually = retryEventuallySteps(initialRunState.nestedContext, conf, 0, Nil)
      // make sure that the inner block does not run forever
      eventually.timeoutTo(after = conf.maxTime * 2, backup = timeoutFailedResult)
    }.map {
      case (run, executionTime) ⇒
        val (retries, retriedRunState, report) = run
        val initialDepth = initialRunState.depth
        val (fullLogs, xor) = report.fold(
          failedStep ⇒ {
            val fullLogs = failedTitleLog(initialDepth) +: retriedRunState.logs :+ FailureLogInstruction(s"Eventually block did not complete in time after being retried '$retries' times", initialDepth, Some(executionTime))
            (fullLogs, Left(failedStep))
          },
          _ ⇒ {
            val fullLogs = successTitleLog(initialDepth) +: retriedRunState.logs :+ SuccessLogInstruction(s"Eventually block succeeded after '$retries' retries", initialDepth, Some(executionTime))
            (fullLogs, rightDone)
          }
        )
        (initialRunState.mergeNested(retriedRunState, fullLogs), xor)
    }
  }
}

case class EventuallyConf(maxTime: FiniteDuration, interval: FiniteDuration) {
  def consume(burnt: FiniteDuration) = {
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