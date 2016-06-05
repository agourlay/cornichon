package com.github.agourlay.cornichon.steps.wrapped

import com.github.agourlay.cornichon.core._

import scala.annotation.tailrec
import scala.concurrent.ExecutionContext

case class RepeatStep(nested: Vector[Step], occurence: Int) extends WrapperStep {

  require(occurence > 0, "repeat block must contain a positive number of occurence")

  val title = s"Repeat block with occurence '$occurence'"

  def run(engine: Engine, session: Session, depth: Int)(implicit ec: ExecutionContext) = {

    @tailrec
    def repeatSuccessSteps(session: Session, retriesNumber: Long = 0): (Long, StepsResult) = {
      engine.runSteps(nested, session, Vector.empty, depth + 1) match {
        case s: SuccessStepsResult ⇒
          if (retriesNumber == occurence - 1) (retriesNumber, s)
          else repeatSuccessSteps(s.session, retriesNumber + 1)
        case f: FailureStepsResult ⇒
          // In case of failure only the logs of the last run are shown to avoid giant traces.
          (retriesNumber, f)
      }
    }

    val (repeatRes, executionTime) = engine.withDuration {
      repeatSuccessSteps(session)
    }

    val (retries, report) = repeatRes

    report match {
      case s: SuccessStepsResult ⇒
        val fullLogs = successTitleLog(depth) +: report.logs :+ SuccessLogInstruction(s"Repeat block with occurence '$occurence' succeeded", depth, Some(executionTime))
        SuccessStepsResult(report.session, fullLogs)
      case f: FailureStepsResult ⇒
        val fullLogs = failedTitleLog(depth) +: report.logs :+ FailureLogInstruction(s"Repeat block with occurence '$occurence' failed after '$retries' occurence", depth, Some(executionTime))
        val failedStep = FailedStep(f.failedStep.step, RepeatBlockContainFailedSteps)
        FailureStepsResult(failedStep, fullLogs, report.session)
    }
  }
}