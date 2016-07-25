package com.github.agourlay.cornichon.steps.wrapped

import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.core.Engine._

import scala.annotation.tailrec
import scala.concurrent.ExecutionContext

case class RepeatStep(nested: Vector[Step], occurrence: Int) extends WrapperStep {

  require(occurrence > 0, "repeat block must contain a positive number of occurence")

  val title = s"Repeat block with occurrence '$occurrence'"

  def run(engine: Engine, session: Session, depth: Int)(implicit ec: ExecutionContext) = {

    @tailrec
    def repeatSuccessSteps(session: Session, retriesNumber: Long = 0): (Long, Session, StepsResult) = {
      val (newSession, stepResult) = engine.runSteps(nested, session, Vector.empty, depth + 1)
      stepResult match {
        case s: SuccessStepsResult ⇒
          if (retriesNumber == occurrence - 1) (retriesNumber, newSession, s)
          else repeatSuccessSteps(newSession, retriesNumber + 1)
        case f: FailureStepsResult ⇒
          // In case of failure only the logs of the last run are shown to avoid giant traces.
          (retriesNumber, newSession, f)
      }
    }

    val ((retries, newSession, report), executionTime) = withDuration {
      repeatSuccessSteps(session)
    }

    report match {
      case s: SuccessStepsResult ⇒
        val fullLogs = successTitleLog(depth) +: report.logs :+ SuccessLogInstruction(s"Repeat block with occurrence '$occurrence' succeeded", depth, Some(executionTime))
        (newSession, SuccessStepsResult(fullLogs))
      case f: FailureStepsResult ⇒
        val fullLogs = failedTitleLog(depth) +: report.logs :+ FailureLogInstruction(s"Repeat block with occurrence '$occurrence' failed after '$retries' occurence", depth, Some(executionTime))
        val failedStep = FailedStep(f.failedStep.step, RepeatBlockContainFailedSteps)
        (newSession, FailureStepsResult(failedStep, fullLogs))
    }
  }
}