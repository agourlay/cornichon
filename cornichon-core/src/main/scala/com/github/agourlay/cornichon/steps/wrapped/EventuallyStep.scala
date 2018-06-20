package com.github.agourlay.cornichon.steps.wrapped

import cats.data.NonEmptyList
import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.core.Done._
import com.github.agourlay.cornichon.util.Timing._
import cats.syntax.either._

import monix.eval.Task

import scala.concurrent.duration.{ Duration, FiniteDuration }

case class EventuallyStep(nested: List[Step], conf: EventuallyConf) extends WrapperStep {
  val title = s"Eventually block with maxDuration = ${conf.maxTime} and interval = ${conf.interval}"

  override def run(engine: Engine)(initialRunState: RunState) = {

    def retryEventuallySteps(runState: RunState, conf: EventuallyConf, retriesNumber: Long): Task[(Long, RunState, Either[FailedStep, Done])] = {
      withDuration {
        val nestedTask = engine.runSteps(nested, runState.resetLogs)
        if (retriesNumber == 0) nestedTask else nestedTask.delayExecution(conf.interval)
      }.flatMap {
        case ((newRunState, res), executionTime) ⇒
          val remainingTime = conf.maxTime - executionTime
          res.fold(
            failedStep ⇒ {
              // Check that it could go through another loop ather the interval
              if ((remainingTime - conf.interval).gt(Duration.Zero)) {
                // Only cleanup steps are propagated
                val nextRetryState = runState.prependCleanupStepsFrom(newRunState)
                retryEventuallySteps(nextRetryState, conf.consume(executionTime), retriesNumber + 1)
              } else {
                // In case of failure only the logs of the last run are shown to avoid giant traces.
                Task.now((retriesNumber, newRunState, Left(failedStep)))
              }
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
      val eventually = retryEventuallySteps(initialRunState.nestedContext, conf, 0)
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
