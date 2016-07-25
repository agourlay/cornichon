package com.github.agourlay.cornichon.steps.wrapped

import cats.data.Xor
import cats.data.Xor._

import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.core.Engine._
import com.github.agourlay.cornichon.core.Done._

import scala.annotation.tailrec
import scala.concurrent.ExecutionContext

case class RepeatStep(nested: Vector[Step], occurrence: Int) extends WrapperStep {

  require(occurrence > 0, "repeat block must contain a positive number of occurence")

  val title = s"Repeat block with occurrence '$occurrence'"

  def run(engine: Engine, session: Session, depth: Int)(implicit ec: ExecutionContext) = {

    @tailrec
    def repeatSuccessSteps(session: Session, retriesNumber: Long = 0): (Long, Session, Vector[LogInstruction], Xor[FailedStep, Done]) = {
      val (newSession, logs, stepResult) = engine.runSteps(nested, session, Vector.empty, depth + 1)
      stepResult match {
        case Right(done) ⇒
          if (retriesNumber == occurrence - 1) (retriesNumber, newSession, logs, rightDone)
          else repeatSuccessSteps(newSession, retriesNumber + 1)
        case Left(failed) ⇒
          // In case of failure only the logs of the last run are shown to avoid giant traces.
          (retriesNumber, newSession, logs, left(failed))
      }
    }

    val ((retries, newSession, logs, report), executionTime) = withDuration {
      repeatSuccessSteps(session)
    }

    report match {
      case Right(done) ⇒
        val fullLogs = successTitleLog(depth) +: logs :+ SuccessLogInstruction(s"Repeat block with occurrence '$occurrence' succeeded", depth, Some(executionTime))
        (newSession, fullLogs, rightDone)
      case Left(failedStep) ⇒
        val fullLogs = failedTitleLog(depth) +: logs :+ FailureLogInstruction(s"Repeat block with occurrence '$occurrence' failed after '$retries' occurence", depth, Some(executionTime))
        val artificialFailedStep = FailedStep(failedStep.step, RepeatBlockContainFailedSteps)
        (newSession, fullLogs, left(artificialFailedStep))
    }
  }
}