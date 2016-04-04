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

  def run(engine: Engine, nextSteps: Vector[Step], session: Session, logs: Vector[LogInstruction], depth: Int)(implicit ec: ExecutionContext) = {

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
          else engine.buildFailedRunSteps(stepsToRetry.last, stepsToRetry, EventuallyBlockSucceedAfterMaxDuration, runLogs, successSession)
        case f @ FailedRunSteps(failed, _, fLogs, fSession) ⇒
          val updatedLogs = accLogs ++ fLogs
          if ((remainingTime - conf.interval).gt(Duration.Zero)) {
            Thread.sleep(conf.interval.toMillis)
            retryEventuallySteps(stepsToRetry, session, conf.consume(executionTime + conf.interval), updatedLogs, depth)
          } else f.copy(logs = updatedLogs, session = fSession)
      }
    }

    val updatedLogs = logs :+ DefaultLogInstruction(title, depth)
    val (res, executionTime) = Engine.withDuration {
      retryEventuallySteps(nested, session, conf, Vector.empty, depth + 1)
    }
    res match {
      case s @ SuccessRunSteps(sSession, sLogs) ⇒
        val fullLogs = updatedLogs ++ sLogs :+ SuccessLogInstruction(s"Eventually block succeeded", depth, Some(executionTime))
        engine.runSteps(nextSteps, sSession, fullLogs, depth)
      case f @ FailedRunSteps(_, _, eLogs, fSession) ⇒
        val fullLogs = (updatedLogs ++ eLogs :+ FailureLogInstruction(s"Eventually block did not complete in time", depth, Some(executionTime))) ++ engine.logNonExecutedStep(nextSteps, depth)
        f.copy(logs = fullLogs, session = fSession)
    }

  }
}
