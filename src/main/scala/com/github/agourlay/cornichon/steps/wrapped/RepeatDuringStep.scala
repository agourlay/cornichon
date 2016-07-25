package com.github.agourlay.cornichon.steps.wrapped

import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.core.Engine._

import scala.annotation.tailrec
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.Duration

case class RepeatDuringStep(nested: Vector[Step], duration: Duration) extends WrapperStep {
  val title = s"Repeat block during '$duration'"

  def run(engine: Engine, session: Session, depth: Int)(implicit ec: ExecutionContext) = {

    @tailrec
    def repeatStepsDuring(steps: Vector[Step], session: Session, duration: Duration, accLogs: Vector[LogInstruction], retriesNumber: Long, depth: Int): (Long, Session, StepsResult) = {
      val ((newSession, res), executionTime) = withDuration {
        engine.runSteps(steps, session, Vector.empty, depth)
      }
      val remainingTime = duration - executionTime
      res match {
        case s @ SuccessStepsResult(sLogs) ⇒
          if (remainingTime.gt(Duration.Zero))
            repeatStepsDuring(steps, newSession, remainingTime, accLogs ++ res.logs, retriesNumber + 1, depth)
          else
            // In case of success all logs are returned but they are not printed by default.
            (retriesNumber, newSession, s.copy(logs = accLogs ++ sLogs))
        case f @ FailureStepsResult(_, eLogs) ⇒
          // In case of failure only the logs of the last run are shown to avoid giant traces.
          (retriesNumber, newSession, f.copy(logs = eLogs))
      }
    }

    val (repeatRes, executionTime) = withDuration {
      repeatStepsDuring(nested, session, duration, Vector.empty, 0, depth + 1)
    }

    val (retries, newSession, report) = repeatRes

    report match {
      case s: SuccessStepsResult ⇒
        val fullLogs = successTitleLog(depth) +: report.logs :+ SuccessLogInstruction(s"Repeat block during '$duration' succeeded after '$retries' retries", depth, Some(executionTime))
        (newSession, SuccessStepsResult(fullLogs))
      case f: FailureStepsResult ⇒
        val fullLogs = failedTitleLog(depth) +: report.logs :+ FailureLogInstruction(s"Repeat block during '$duration' failed after being retried '$retries' times", depth, Some(executionTime))
        val failedStep = FailedStep(f.failedStep.step, RepeatDuringBlockContainFailedSteps)
        (newSession, FailureStepsResult(failedStep, fullLogs))
    }
  }
}
