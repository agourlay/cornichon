package com.github.agourlay.cornichon.steps.wrapped

import com.github.agourlay.cornichon.core._

import scala.annotation.tailrec
import scala.concurrent.ExecutionContext

case class RepeatStep(nested: Vector[Step], occurence: Int) extends WrapperStep {

  require(occurence > 0, "repeat block must contain a positive number of occurence")

  val title = s"Repeat block with occurence '$occurence'"

  def run(engine: Engine, session: Session, depth: Int)(implicit ec: ExecutionContext) = {

    @tailrec
    def repeatSuccessSteps(retriesNumber: Long = 0): (Long, StepsReport) = {
      engine.runSteps(nested, session, Vector.empty, depth + 1) match {
        case s: SuccessRunSteps ⇒
          if (retriesNumber == occurence - 1) (retriesNumber, s)
          else repeatSuccessSteps(retriesNumber + 1)
        case f: FailedRunSteps ⇒
          // In case of failure only the logs of the last run are shown to avoid giant traces.
          (retriesNumber, f)
      }
    }

    val titleLog = InfoLogInstruction(title, depth)
    val (repeatRes, executionTime) = engine.withDuration {
      // Session not propagated through repeat calls
      repeatSuccessSteps()
    }

    val (retries, report) = repeatRes

    report match {
      case s: SuccessRunSteps ⇒
        val fullLogs = titleLog +: report.logs :+ SuccessLogInstruction(s"Repeat block with occurence '$occurence' succeeded", depth, Some(executionTime))
        SuccessRunSteps(report.session, fullLogs)
      case f: FailedRunSteps ⇒
        val fullLogs = titleLog +: report.logs :+ FailureLogInstruction(s"Repeat block with occurence '$occurence' failed after '$retries' occurence", depth, Some(executionTime))
        FailedRunSteps(f.step, RepeatBlockContainFailedSteps, fullLogs, report.session)
    }
  }
}