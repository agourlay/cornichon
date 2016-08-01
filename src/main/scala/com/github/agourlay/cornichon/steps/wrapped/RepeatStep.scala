package com.github.agourlay.cornichon.steps.wrapped

import cats.data.Xor
import cats.data.Xor._

import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.core.Done._
import com.github.agourlay.cornichon.util.Timing._

import scala.annotation.tailrec
import scala.concurrent.ExecutionContext

case class RepeatStep(nested: Vector[Step], occurrence: Int) extends WrapperStep {

  require(occurrence > 0, "repeat block must contain a positive number of occurence")

  val title = s"Repeat block with occurrence '$occurrence'"

  override def run(engine: Engine)(initialRunState: RunState)(implicit ec: ExecutionContext) = {

    @tailrec
    def repeatSuccessSteps(retriesNumber: Long, runState: RunState): (Long, RunState, Xor[FailedStep, Done]) = {
      // reset logs at each loop to have the possibility to not aggregate in failure case
      val (onceMoreRunState, stepResult) = engine.runSteps(runState.resetLogs)
      stepResult match {
        case Right(done) ⇒
          val successState = runState.withSession(onceMoreRunState.session).appendLogs(onceMoreRunState.logs)
          // only show last successful run to avoid giant traces.
          if (retriesNumber == occurrence - 1) (retriesNumber, successState, rightDone)
          else repeatSuccessSteps(retriesNumber + 1, runState.withSession(onceMoreRunState.session))
        case Left(failed) ⇒
          // In case of failure only the logs of the last run are shown to avoid giant traces.
          (retriesNumber, onceMoreRunState, left(failed))
      }
    }

    val ((retries, repeatedState, report), executionTime) = withDuration {
      val bootstrapRepeatState = initialRunState.withSteps(nested).resetLogs.goDeeper
      repeatSuccessSteps(0, bootstrapRepeatState)
    }

    val depth = initialRunState.depth

    val (fullLogs, xor) = report match {
      case Right(done) ⇒
        val fullLogs = successTitleLog(depth) +: repeatedState.logs :+ SuccessLogInstruction(s"Repeat block with occurrence '$occurrence' succeeded", depth, Some(executionTime))
        (fullLogs, rightDone)
      case Left(failedStep) ⇒
        val fullLogs = failedTitleLog(depth) +: repeatedState.logs :+ FailureLogInstruction(s"Repeat block with occurrence '$occurrence' failed after '$retries' occurence", depth, Some(executionTime))
        val artificialFailedStep = FailedStep(failedStep.step, RepeatBlockContainFailedSteps)
        (fullLogs, left(artificialFailedStep))
    }

    (initialRunState.withSession(repeatedState.session).appendLogs(fullLogs), xor)
  }
}