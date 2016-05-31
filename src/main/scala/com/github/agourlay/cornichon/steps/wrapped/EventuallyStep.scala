package com.github.agourlay.cornichon.steps.wrapped

import com.github.agourlay.cornichon.core._

import scala.annotation.tailrec
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.Duration

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

case class EventuallyStep(nested: Vector[Step], conf: EventuallyConf) extends WrapperStep {
  val title = s"Eventually block with maxDuration = ${conf.maxTime} and interval = ${conf.interval}"

  def run(engine: Engine, session: Session, depth: Int)(implicit ec: ExecutionContext) = {

    @tailrec
    def retryEventuallySteps(stepsToRetry: Vector[Step], session: Session, conf: EventuallyConf, accLogs: Vector[LogInstruction], retriesNumber: Long, depth: Int): (Long, StepsReport) = {
      val (res, executionTime) = engine.withDuration {
        engine.runSteps(stepsToRetry, session, Vector.empty, depth)
      }
      val remainingTime = conf.maxTime - executionTime
      res match {
        case s @ SuccessRunSteps(successSession, sLogs) ⇒
          val runLogs = accLogs ++ sLogs
          if (remainingTime.gt(Duration.Zero)) {
            // In case of success all logs are returned but they are not printed by default.
            (retriesNumber, s.copy(logs = runLogs))
          } else {
            // Run was a success but the time is up.
            (retriesNumber, FailedRunSteps(stepsToRetry.last, EventuallyBlockSucceedAfterMaxDuration, runLogs, successSession))
          }

        case f @ FailedRunSteps(_, _, fLogs, fSession) ⇒
          if ((remainingTime - conf.interval).gt(Duration.Zero)) {
            Thread.sleep(conf.interval.toMillis)
            retryEventuallySteps(stepsToRetry, session, conf.consume(executionTime + conf.interval), accLogs ++ fLogs, retriesNumber + 1, depth)
          } else {
            // In case of failure only the logs of the last run are shown to avoid giant traces.
            (retriesNumber, f.copy(logs = fLogs, session = fSession))
          }
      }
    }

    val (res, executionTime) = engine.withDuration {
      retryEventuallySteps(nested, session, conf, Vector.empty, 0, depth + 1)
    }

    val (retries, report) = res

    report match {
      case s @ SuccessRunSteps(sSession, sLogs) ⇒
        val fullLogs = successTitleLog(depth) +: sLogs :+ SuccessLogInstruction(s"Eventually block succeeded after '$retries' retries", depth, Some(executionTime))
        s.copy(logs = fullLogs)
      case f @ FailedRunSteps(_, _, eLogs, fSession) ⇒
        val fullLogs = failedTitleLog(depth) +: eLogs :+ FailureLogInstruction(s"Eventually block did not complete in time after being retried '$retries' times", depth, Some(executionTime))
        f.copy(logs = fullLogs, session = fSession)
    }
  }
}
