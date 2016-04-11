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
    def retryEventuallySteps(stepsToRetry: Vector[Step], session: Session, conf: EventuallyConf, accLogs: Vector[LogInstruction], depth: Int): StepsReport = {
      val (res, executionTime) = engine.withDuration {
        engine.runSteps(stepsToRetry, session, Vector.empty, depth)
      }
      val remainingTime = conf.maxTime - executionTime
      res match {
        case s @ SuccessRunSteps(successSession, sLogs) ⇒
          val runLogs = accLogs ++ sLogs
          if (remainingTime.gt(Duration.Zero)) s.copy(logs = runLogs)
          else FailedRunSteps(stepsToRetry.last, EventuallyBlockSucceedAfterMaxDuration, runLogs, successSession)
        case f @ FailedRunSteps(_, _, fLogs, fSession) ⇒
          val updatedLogs = accLogs ++ fLogs
          if ((remainingTime - conf.interval).gt(Duration.Zero)) {
            Thread.sleep(conf.interval.toMillis)
            retryEventuallySteps(stepsToRetry, session, conf.consume(executionTime + conf.interval), updatedLogs, depth)
          } else f.copy(logs = updatedLogs, session = fSession)
      }
    }

    val titleLogs = DefaultLogInstruction(title, depth)
    val (res, executionTime) = engine.withDuration {
      retryEventuallySteps(nested, session, conf, Vector.empty, depth + 1)
    }
    res match {
      case s @ SuccessRunSteps(sSession, sLogs) ⇒
        val fullLogs = titleLogs +: sLogs :+ SuccessLogInstruction(s"Eventually block succeeded", depth, Some(executionTime))
        s.copy(logs = fullLogs)
      case f @ FailedRunSteps(_, _, eLogs, fSession) ⇒
        val fullLogs = titleLogs +: eLogs :+ FailureLogInstruction(s"Eventually block did not complete in time", depth, Some(executionTime))
        f.copy(logs = fullLogs, session = fSession)
    }
  }
}
