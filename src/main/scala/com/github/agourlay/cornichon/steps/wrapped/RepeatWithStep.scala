package com.github.agourlay.cornichon.steps.wrapped

import java.util.Timer

import cats.Show
import com.github.agourlay.cornichon.core.Done.rightDone
import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.util.Timing.withDuration

import scala.concurrent.{ ExecutionContext, Future }
import cats.syntax.show._

case class RepeatWithStep[A: Show](nested: List[Step], elements: Seq[A], elementName: String) extends WrapperStep {

  require(elements.nonEmpty, "repeatWith block must contain a non empty sequence of elements")

  val printElements = s"[${elements.map(_.show).mkString(", ")}]"
  val title = s"RepeatWith block with elements $printElements"

  override def run(engine: Engine)(initialRunState: RunState)(implicit ec: ExecutionContext, timer: Timer) = {

    def repeatSuccessSteps(remainingElements: Seq[A], runState: RunState): Future[(RunState, Either[(A, FailedStep), Done])] = {
      remainingElements.headOption.fold[Future[(RunState, Either[(A, FailedStep), Done])]](Future.successful(runState, rightDone)) { element ⇒
        // reset logs at each loop to have the possibility to not aggregate in failure case
        val rs = runState.resetLogs
        val runStateWithIndex = rs.addToSession(elementName, element.show)
        engine.runSteps(runStateWithIndex).flatMap {
          case (onceMoreRunState, stepResult) ⇒
            stepResult match {
              case Right(_) ⇒
                val successState = runState.withSession(onceMoreRunState.session).appendLogsFrom(onceMoreRunState)
                repeatSuccessSteps(remainingElements.tail, successState)
              case Left(failed) ⇒
                // In case of failure only the logs of the last run are shown to avoid giant traces.
                Future.successful((onceMoreRunState, Left((element, failed))))
            }
        }
      }
    }

    withDuration {
      val bootstrapRepeatState = initialRunState.forNestedSteps(nested)
      repeatSuccessSteps(elements, bootstrapRepeatState)
    }.map {
      case (run, executionTime) ⇒

        val (repeatedState, report) = run
        val depth = initialRunState.depth

        val (fullLogs, xor) = report match {
          case Right(_) ⇒
            val fullLogs = successTitleLog(depth) +: repeatedState.logs :+ SuccessLogInstruction(s"RepeatWith block with elements $printElements succeeded", depth, Some(executionTime))
            (fullLogs, rightDone)
          case Left((failedElement, failedStep)) ⇒
            val fullLogs = failedTitleLog(depth) +: repeatedState.logs :+ FailureLogInstruction(s"RepeatWith block with elements $printElements failed at element '${failedElement.show}'", depth, Some(executionTime))
            val artificialFailedStep = FailedStep(failedStep.step, RepeatWithBlockContainFailedSteps(failedElement, failedStep.error))
            (fullLogs, Left(artificialFailedStep))
        }

        (initialRunState.withSession(repeatedState.session).appendLogs(fullLogs), xor)
    }
  }
}

case class RepeatWithBlockContainFailedSteps[A: Show](element: A, error: CornichonError) extends CornichonError {
  val msg = s"RepeatWith block failed for element '${element.show}' with error:\n${error.msg}"
}
