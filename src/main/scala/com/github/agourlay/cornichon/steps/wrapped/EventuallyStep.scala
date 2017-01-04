package com.github.agourlay.cornichon.steps.wrapped

import java.util.concurrent.ScheduledExecutorService

import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.core.Done._
import com.github.agourlay.cornichon.util.Timeouts
import com.github.agourlay.cornichon.util.Timing._

import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration.{ Duration, FiniteDuration }

case class EventuallyStep(nested: List[Step], conf: EventuallyConf) extends WrapperStep {
  val title = s"Eventually block with maxDuration = ${conf.maxTime} and interval = ${conf.interval}"

  override def run(engine: Engine)(initialRunState: RunState)(implicit ec: ExecutionContext, timer: ScheduledExecutorService) = {

    def retryEventuallySteps(runState: RunState, conf: EventuallyConf, retriesNumber: Long): Future[(Long, RunState, Either[FailedStep, Done])] = {
      withDuration {
        // reset logs at each loop to have the possibility to not aggregate in failure case
        val retryRunState = runState.resetLogs
        engine.runSteps(retryRunState)
      }.flatMap {
        case (run, executionTime) ⇒
          val (newRunState, res) = run
          val remainingTime = conf.maxTime - executionTime
          res match {
            case Left(failedStep) ⇒
              if ((remainingTime - conf.interval).gt(Duration.Zero)) {
                Timeouts.timeout(conf.interval) {
                  retryEventuallySteps(runState.appendLogsFrom(newRunState), conf.consume(executionTime + conf.interval), retriesNumber + 1)
                }
              } else {
                // In case of failure only the logs of the last run are shown to avoid giant traces.
                Future.successful(retriesNumber, newRunState, Left(failedStep))
              }

            case Right(_) ⇒
              val state = runState.withSession(newRunState.session).appendLogsFrom(newRunState)
              if (remainingTime.gt(Duration.Zero)) {
                // In case of success all logs are returned but they are not printed by default.
                Future.successful(retriesNumber, state, rightDone)
              } else {
                // Run was a success but the time is up.
                val failedStep = FailedStep.fromSingle(runState.remainingSteps.last, EventuallyBlockSucceedAfterMaxDuration)
                Future.successful(retriesNumber, state, Left(failedStep))
              }
          }
      }
    }

    withDuration {
      val initialRetryState = initialRunState.forNestedSteps(nested)
      retryEventuallySteps(initialRetryState, conf, 0)
    }.map {
      case (run, executionTime) ⇒

        val (retries, retriedRunState, report) = run
        val initialDepth = initialRunState.depth

        val (fullLogs, xor) = report.fold(
          failedStep ⇒ {
            val fullLogs = failedTitleLog(initialDepth) +: retriedRunState.logs :+ FailureLogInstruction(s"Eventually block did not complete in time after being retried '$retries' times", initialDepth, Some(executionTime))
            (fullLogs, Left(failedStep))
          },
          done ⇒ {
            val fullLogs = successTitleLog(initialDepth) +: retriedRunState.logs :+ SuccessLogInstruction(s"Eventually block succeeded after '$retries' retries", initialDepth, Some(executionTime))
            (fullLogs, rightDone)
          }
        )

        (initialRunState.withSession(retriedRunState.session).appendLogs(fullLogs), xor)
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
  def empty = EventuallyConf(Duration.Zero, Duration.Zero)
}

case object EventuallyBlockSucceedAfterMaxDuration extends CornichonError {
  val baseErrorMessage = "Eventually block succeeded after 'maxDuration'"
}
