package com.github.agourlay.cornichon.steps.wrapped

import cats.data.{ NonEmptyList, StateT }
import cats.effect.IO
import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.json.CornichonJson
import com.github.agourlay.cornichon.core.ScenarioRunner._
import com.github.agourlay.cornichon.core.Done._
import com.github.agourlay.cornichon.util.Printing._

case class WithDataInputStep(nested: List[Step], where: String, rawJson: Boolean = false) extends WrapperStep {

  val title = s"With data input block $where"

  override val stateUpdate: StepState = StateT { runState =>

    def runInputs(inputs: List[List[(String, String)]], runState: RunState): IO[(RunState, Either[(List[(String, String)], FailedStep), Done])] =
      if (inputs.isEmpty) IO.pure((runState, rightDone))
      else {
        val currentInputs = inputs.head
        val runInfo = InfoLogInstruction(s"Run with inputs ${printArrowPairs(currentInputs)}", runState.depth)
        val bootstrapFilledInput = runState.addToSession(currentInputs).withLog(runInfo).goDeeper
        ScenarioRunner.runStepsShortCircuiting(nested, bootstrapFilledInput).flatMap {
          case (filledState, stepsResult) =>
            stepsResult.fold(
              failedStep => {
                // Prepend previous logs
                IO.pure((runState.mergeNested(filledState), Left((currentInputs, failedStep))))
              },
              _ => {
                // Logs are propagated but not the session
                runInputs(inputs.tail, runState.recordLogStack(filledState.logStack))
              }
            )
        }
      }

    runState.scenarioContext.fillPlaceholders(where)
      .flatMap(CornichonJson.parseDataTable)
      .fold(
        t => IO.pure(handleErrors(this, runState, NonEmptyList.one(t))),
        parsedTable => {
          val inputs = parsedTable.map { line =>
            line.toIterable
              .map { case (key, json) => (key, if (rawJson) json.noSpacesSortKeys else CornichonJson.jsonStringValue(json)) }
              .toList
          }

          runInputs(inputs, runState.nestedContext)
            .timed
            .map {
              case (executionTime, (inputsState, inputsRes)) =>
                val initialDepth = runState.depth
                val (logStack, res) = inputsRes match {
                  case Right(_) =>
                    val wrappedLogStack = SuccessLogInstruction("With data input succeeded for all inputs", initialDepth, Some(executionTime)) +: inputsState.logStack :+ successTitleLog(initialDepth)
                    (wrappedLogStack, rightDone)
                  case Left((failedInputs, failedStep)) =>
                    val wrappedLogStack = FailureLogInstruction("With data input failed for one input", initialDepth, Some(executionTime)) +: inputsState.logStack :+ failedTitleLog(initialDepth)
                    val artificialFailedStep = FailedStep.fromSingle(failedStep.step, WithDataInputBlockFailedStep(failedInputs, failedStep.errors))
                    (wrappedLogStack, Left(artificialFailedStep))
                }
                (runState.mergeNested(inputsState, logStack), res)
            }
        }
      )
  }
}

case class WithDataInputBlockFailedStep(failedInputs: List[(String, String)], errors: NonEmptyList[CornichonError]) extends CornichonError {
  lazy val baseErrorMessage = s"WithDataInput block failed for inputs ${printArrowPairs(failedInputs)}"
  override val causedBy = errors.toList
}
