package com.github.agourlay.cornichon.steps.wrapped

import java.util.Timer

import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.core.Done._
import com.github.agourlay.cornichon.util.Timing._

import scala.concurrent.{ ExecutionContext, Future }

case class RepeatStep(nested: List[Step], occurrence: Int, indiceName: Option[String]) extends WrapperStep {

  require(occurrence > 0, "repeat block must contain a positive number of occurence")

  val title = s"Repeat block with occurrence '$occurrence'"

  override def run(engine: Engine)(initialRunState: RunState)(implicit ec: ExecutionContext, timer: Timer) = {

    def repeatSuccessSteps(retriesNumber: Long, runState: RunState): Future[(Long, RunState, Either[FailedStep, Done])] = {
      // reset logs at each loop to have the possibility to not aggregate in failure case
      val rs = runState.resetLogs
      val runStateWithIndex = indiceName.fold(rs)(in ⇒ rs.addToSession(in, (retriesNumber + 1).toString))
      engine.runSteps(runStateWithIndex).flatMap {
        case (onceMoreRunState, stepResult) ⇒
          stepResult match {
            case Right(_) ⇒
              val successState = runState.withSession(onceMoreRunState.session).appendLogsFrom(onceMoreRunState)
              // only show last successful run to avoid giant traces.
              if (retriesNumber == occurrence - 1) Future.successful(retriesNumber, successState, rightDone)
              else repeatSuccessSteps(retriesNumber + 1, runState.withSession(onceMoreRunState.session))
            case Left(failed) ⇒
              // In case of failure only the logs of the last run are shown to avoid giant traces.
              Future.successful(retriesNumber, onceMoreRunState, Left(failed))
          }
      }
    }

    withDuration {
      val bootstrapRepeatState = initialRunState.forNestedSteps(nested)
      repeatSuccessSteps(0, bootstrapRepeatState)
    }.map {
      case (run, executionTime) ⇒

        val (retries, repeatedState, report) = run
        val depth = initialRunState.depth

        val (fullLogs, xor) = report match {
          case Right(_) ⇒
            val fullLogs = successTitleLog(depth) +: repeatedState.logs :+ SuccessLogInstruction(s"Repeat block with occurrence '$occurrence' succeeded", depth, Some(executionTime))
            (fullLogs, rightDone)
          case Left(failedStep) ⇒
            val fullLogs = failedTitleLog(depth) +: repeatedState.logs :+ FailureLogInstruction(s"Repeat block with occurrence '$occurrence' failed after '$retries' occurence", depth, Some(executionTime))
            val artificialFailedStep = FailedStep(failedStep.step, RepeatBlockContainFailedSteps(retries, failedStep.error))
            (fullLogs, Left(artificialFailedStep))
        }

        (initialRunState.withSession(repeatedState.session).appendLogs(fullLogs), xor)
    }
  }
}

case class RepeatBlockContainFailedSteps(failedOccurence: Long, error: CornichonError) extends CornichonError {
  val msg = s"Repeat block failed at occurence $failedOccurence with error:\n${error.msg}"
}
