package com.github.agourlay.cornichon.steps.wrapped

import cats.data.Xor
import cats.data.Xor._
import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.core.Done._
import com.github.agourlay.cornichon.util.Timing._

import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration.Duration

case class EventuallyStep(nested: Vector[Step], conf: EventuallyConf) extends WrapperStep {
  val title = s"Eventually block with maxDuration = ${conf.maxTime} and interval = ${conf.interval}"

  override def run(engine: Engine)(initialRunState: RunState)(implicit ec: ExecutionContext) = {

    def retryEventuallySteps(runState: RunState, conf: EventuallyConf, retriesNumber: Long): Future[(Long, RunState, Xor[FailedStep, Done])] = {
      withDuration {
        val retryRunState = RunState(runState.remainingSteps, runState.session, Vector.empty, runState.depth)
        engine.runSteps(retryRunState)
      }.flatMap {
        case (run, executionTime) ⇒
          val (newRunState, res) = run
          val remainingTime = conf.maxTime - executionTime
          res match {
            case Left(failedStep) ⇒
              if ((remainingTime - conf.interval).gt(Duration.Zero)) {
                Thread.sleep(conf.interval.toMillis)
                retryEventuallySteps(runState.appendLogs(newRunState.logs), conf.consume(executionTime + conf.interval), retriesNumber + 1)
              } else {
                // In case of failure only the logs of the last run are shown to avoid giant traces.
                Future.successful(retriesNumber, newRunState, left(failedStep))
              }

            case Right(_) ⇒
              val state = runState.withSession(newRunState.session).appendLogs(newRunState.logs)
              if (remainingTime.gt(Duration.Zero)) {
                // In case of success all logs are returned but they are not printed by default.
                Future.successful(retriesNumber, state, rightDone)
              } else {
                // Run was a success but the time is up.
                val failedStep = FailedStep(runState.remainingSteps.last, EventuallyBlockSucceedAfterMaxDuration)
                Future.successful(retriesNumber, state, left(failedStep))
              }
          }
      }
    }

    withDuration {
      val initialRetryState = initialRunState.withSteps(nested).resetLogs.goDeeper
      retryEventuallySteps(initialRetryState, conf, 0)
    }.map {
      case (run, executionTime) ⇒

        val (retries, retriedRunState, report) = run
        val initialDepth = initialRunState.depth

        val (fullLogs, xor) = report.fold(
          failedStep ⇒ {
            val fullLogs = failedTitleLog(initialDepth) +: retriedRunState.logs :+ FailureLogInstruction(s"Eventually block did not complete in time after being retried '$retries' times", initialDepth, Some(executionTime))
            (fullLogs, left(failedStep))
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

case class EventuallyConf(maxTime: Duration, interval: Duration) {
  def consume(burnt: Duration) = {
    val rest = maxTime - burnt
    val newMax = if (rest.lteq(Duration.Zero)) Duration.Zero else rest
    copy(maxTime = newMax)
  }
}

object EventuallyConf {
  def empty = EventuallyConf(Duration.Zero, Duration.Zero)
}

case object EventuallyBlockSucceedAfterMaxDuration extends CornichonError {
  val msg = "eventually block succeeded after 'maxDuration'"
}
