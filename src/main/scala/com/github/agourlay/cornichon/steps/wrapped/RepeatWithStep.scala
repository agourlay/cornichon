package com.github.agourlay.cornichon.steps.wrapped

import akka.actor.Scheduler
import cats.data.NonEmptyList
import com.github.agourlay.cornichon.core.Done.rightDone
import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.util.Timing.withDuration

import scala.concurrent.{ ExecutionContext, Future }

case class RepeatWithStep(nested: List[Step], elements: Seq[String], elementName: String) extends WrapperStep {

  require(elements.nonEmpty, "repeatWith block must contain a non empty sequence of elements")

  val printElements = s"[${elements.mkString(", ")}]"
  val title = s"RepeatWith block with elements $printElements"

  override def run(engine: Engine)(initialRunState: RunState)(implicit ec: ExecutionContext, scheduler: Scheduler) = {

    def repeatSuccessSteps(remainingElements: Seq[String], runState: RunState): Future[(RunState, Either[(String, FailedStep), Done])] = {
      remainingElements.headOption.fold[Future[(RunState, Either[(String, FailedStep), Done])]](Future.successful(runState, rightDone)) { element ⇒
        // reset logs at each loop to have the possibility to not aggregate in failure case
        val rs = runState.resetLogs
        val runStateWithIndex = rs.addToSession(elementName, element)
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
            val fullLogs = failedTitleLog(depth) +: repeatedState.logs :+ FailureLogInstruction(s"RepeatWith block with elements $printElements failed at element '$failedElement'", depth, Some(executionTime))
            val artificialFailedStep = FailedStep.fromSingle(failedStep.step, RepeatWithBlockContainFailedSteps(failedElement, failedStep.errors))
            (fullLogs, Left(artificialFailedStep))
        }

        (initialRunState.withSession(repeatedState.session).appendLogs(fullLogs), xor)
    }
  }
}

case class RepeatWithBlockContainFailedSteps(element: String, errors: NonEmptyList[CornichonError]) extends CornichonError {
  val baseErrorMessage = s"RepeatWith block failed for element '$element'"
  override val causedBy = Some(errors)
}
