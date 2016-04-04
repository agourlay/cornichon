package com.github.agourlay.cornichon.steps.wrapped

import com.github.agourlay.cornichon.core._

import scala.annotation.tailrec
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.Duration

case class RepeatDuringStep(nested: Vector[Step], duration: Duration) extends WrapperStep {
  val title = s"Repeat block during '$duration'"

  def run(engine: Engine, nextSteps: Vector[Step], session: Session, logs: Vector[LogInstruction], depth: Int)(implicit ec: ExecutionContext) = {

    @tailrec
    def repeatStepsDuring(steps: Vector[Step], session: Session, duration: Duration, accLogs: Vector[LogInstruction], depth: Int): StepsReport = {
      val (res, executionTime) = engine.withDuration {
        engine.runSteps(steps, session, Vector.empty, depth)
      }
      val remainingTime = duration - executionTime
      if (remainingTime.gt(Duration.Zero))
        repeatStepsDuring(steps, session, remainingTime, accLogs ++ res.logs, depth)
      else
        res match {
          case s @ SuccessRunSteps(sSession, sLogs)      ⇒ s.copy(logs = accLogs ++ sLogs)
          case f @ FailedRunSteps(_, _, eLogs, fSession) ⇒ f.copy(logs = accLogs ++ eLogs)
        }
    }

    val updatedLogs = logs :+ DefaultLogInstruction(title, depth)
    val (repeatRes, executionTime) = engine.withDuration {
      repeatStepsDuring(nested, session, duration, Vector.empty, depth + 1)
    }

    if (repeatRes.isSuccess) {
      val fullLogs = (updatedLogs ++ repeatRes.logs) :+ SuccessLogInstruction(s"Repeat block during $duration succeeded", depth, Some(executionTime))
      engine.runSteps(nextSteps, repeatRes.session, fullLogs, depth)
    } else {
      val fullLogs = (updatedLogs ++ repeatRes.logs) :+ FailureLogInstruction(s"Repeat block during $duration failed", depth, Some(executionTime))
      engine.buildFailedRunSteps(nested.last, nextSteps, RepeatDuringBlockContainFailedSteps, fullLogs, repeatRes.session)
    }
  }
}
