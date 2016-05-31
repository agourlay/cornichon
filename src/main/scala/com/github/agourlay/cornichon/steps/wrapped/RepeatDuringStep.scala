package com.github.agourlay.cornichon.steps.wrapped

import com.github.agourlay.cornichon.core._

import scala.annotation.tailrec
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.Duration

case class RepeatDuringStep(nested: Vector[Step], duration: Duration) extends WrapperStep {
  val title = s"Repeat block during '$duration'"

  def run(engine: Engine, session: Session, depth: Int)(implicit ec: ExecutionContext) = {

    @tailrec
    def repeatStepsDuring(steps: Vector[Step], session: Session, duration: Duration, accLogs: Vector[LogInstruction], retriesNumber: Long, depth: Int): (Long, StepsReport) = {
      val (res, executionTime) = engine.withDuration {
        engine.runSteps(steps, session, Vector.empty, depth)
      }
      val remainingTime = duration - executionTime
      res match {
        case s @ SuccessRunSteps(sSession, sLogs) ⇒
          if (remainingTime.gt(Duration.Zero))
            repeatStepsDuring(steps, sSession, remainingTime, accLogs ++ res.logs, retriesNumber + 1, depth)
          else
            // In case of success all logs are returned but they are not printed by default.
            (retriesNumber, s.copy(logs = accLogs ++ sLogs))
        case f @ FailedRunSteps(_, _, eLogs, _) ⇒
          // In case of failure only the logs of the last run are shown to avoid giant traces.
          (retriesNumber, f.copy(logs = eLogs))
      }
    }

    val (repeatRes, executionTime) = engine.withDuration {
      repeatStepsDuring(nested, session, duration, Vector.empty, 0, depth + 1)
    }

    val (retries, report) = repeatRes

    report match {
      case s: SuccessRunSteps ⇒
        val fullLogs = successTitleLog(depth) +: report.logs :+ SuccessLogInstruction(s"Repeat block during '$duration' succeeded after '$retries' retries", depth, Some(executionTime))
        SuccessRunSteps(report.session, fullLogs)
      case f: FailedRunSteps ⇒
        val fullLogs = failedTitleLog(depth) +: report.logs :+ FailureLogInstruction(s"Repeat block during '$duration' failed after being retried '$retries' times", depth, Some(executionTime))
        FailedRunSteps(f.step, RepeatDuringBlockContainFailedSteps, fullLogs, report.session)
    }
  }
}
