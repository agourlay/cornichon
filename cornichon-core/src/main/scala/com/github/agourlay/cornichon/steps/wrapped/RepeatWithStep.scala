package com.github.agourlay.cornichon.steps.wrapped

import cats.data.{ NonEmptyList, StateT }
import cats.effect.IO
import com.github.agourlay.cornichon.core.Done.rightDone
import com.github.agourlay.cornichon.core._

case class RepeatWithStep(nested: List[Step], elements: List[String], elementName: String) extends WrapperStep {

  require(elements.nonEmpty, "repeatWith block must contain a non empty sequence of elements")

  private val printElements = s"[${elements.mkString(", ")}]"
  val title = s"RepeatWith block with elements $printElements"

  override val stateUpdate: StepState = StateT { runState =>

    def repeatSuccessSteps(remainingElements: List[String], runState: RunState): IO[(RunState, Either[(String, FailedStep), Done])] =
      remainingElements match {
        case Nil =>
          IO.pure((runState, rightDone))
        case element :: tail =>
          // reset logs at each loop to have the possibility to not aggregate in failure case
          val rs = runState.resetLogStack
          val runStateWithIndex = rs.addToSession(elementName, element)
          ScenarioRunner.runStepsShortCircuiting(nested, runStateWithIndex).flatMap {
            case (onceMoreRunState, stepResult) =>
              stepResult.fold(
                failed => {
                  // In case of failure only the logs of the last run are shown to avoid giant traces.
                  IO.pure((onceMoreRunState, Left((element, failed))))
                },
                _ => {
                  val successState = runState.mergeNested(onceMoreRunState)
                  repeatSuccessSteps(tail, successState)
                }
              )
          }
      }

    repeatSuccessSteps(elements, runState.nestedContext).timed
      .map {
        case (executionTime, (repeatedState, report)) =>
          val depth = runState.depth
          val (logStack, res) = report match {
            case Right(_) =>
              val wrappedLogStack = SuccessLogInstruction(s"RepeatWith block with elements $printElements succeeded", depth, Some(executionTime)) +: repeatedState.logStack :+ successTitleLog(depth)
              (wrappedLogStack, rightDone)
            case Left((failedElement, failedStep)) =>
              val wrappedLogStack = FailureLogInstruction(s"RepeatWith block with elements $printElements failed at element '$failedElement'", depth, Some(executionTime)) +: repeatedState.logStack :+ failedTitleLog(depth)
              val artificialFailedStep = FailedStep.fromSingle(failedStep.step, RepeatWithBlockContainFailedSteps(failedElement, failedStep.errors))
              (wrappedLogStack, Left(artificialFailedStep))
          }
          (runState.mergeNested(repeatedState, logStack), res)
      }
  }
}

case class RepeatWithBlockContainFailedSteps(element: String, errors: NonEmptyList[CornichonError]) extends CornichonError {
  lazy val baseErrorMessage = s"RepeatWith block failed for element '$element'"
  override val causedBy = errors.toList
}
