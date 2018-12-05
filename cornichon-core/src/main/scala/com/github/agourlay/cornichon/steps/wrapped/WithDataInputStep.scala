package com.github.agourlay.cornichon.steps.wrapped

import cats.data.NonEmptyList
import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.json.CornichonJson
import com.github.agourlay.cornichon.core.Engine._
import com.github.agourlay.cornichon.core.Done._
import com.github.agourlay.cornichon.core.core.StepResult
import com.github.agourlay.cornichon.resolver.PlaceholderResolver
import com.github.agourlay.cornichon.util.Timing._
import com.github.agourlay.cornichon.util.Printing._
import monix.eval.Task

case class WithDataInputStep(nested: List[Step], where: String, r: PlaceholderResolver) extends WrapperStep {

  val title = s"With data input block $where"

  override def run(engine: Engine)(initialRunState: RunState): StepResult = {

    def runInputs(inputs: List[List[(String, String)]], runState: RunState): Task[(RunState, Either[(List[(String, String)], FailedStep), Done])] =
      if (inputs.isEmpty) Task.now((runState, rightDone))
      else {
        val currentInputs = inputs.head
        val runInfo = InfoLogInstruction(s"Run with inputs ${printArrowPairs(currentInputs)}", runState.depth)
        val bootstrapFilledInput = runState.addToSession(currentInputs).withLog(runInfo).goDeeper
        engine.runSteps(nested, bootstrapFilledInput).flatMap {
          case (filledState, stepsResult) ⇒
            stepsResult.fold(
              failedStep ⇒ {
                // Prepend previous logs
                Task.now((runState.mergeNested(filledState), Left((currentInputs, failedStep))))
              },
              _ ⇒ {
                // Logs are propagated but not the session
                runInputs(inputs.tail, runState.recordLogStack(filledState.logStack))
              }
            )
        }
      }

    r.fillPlaceholders(where)(initialRunState.session)
      .flatMap(CornichonJson.parseDataTable)
      .fold(
        t ⇒ Task.now(handleErrors(this, initialRunState, NonEmptyList.one(t))),
        parsedTable ⇒ {
          val inputs = parsedTable.map { line ⇒
            line.toList.map { case (key, json) ⇒ (key, CornichonJson.jsonStringValue(json)) }
          }

          withDuration {
            runInputs(inputs, initialRunState.nestedContext)
          }.map {
            case ((inputsState, inputsRes), executionTime) ⇒
              val initialDepth = initialRunState.depth
              val (logStack, res) = inputsRes match {
                case Right(_) ⇒
                  val wrappedLogStack = SuccessLogInstruction("With data input succeeded for all inputs", initialDepth, Some(executionTime)) +: inputsState.logStack :+ successTitleLog(initialDepth)
                  (wrappedLogStack, rightDone)
                case Left((failedInputs, failedStep)) ⇒
                  val wrappedLogStack = FailureLogInstruction("With data input failed for one input", initialDepth, Some(executionTime)) +: inputsState.logStack :+ failedTitleLog(initialDepth)
                  val artificialFailedStep = FailedStep.fromSingle(failedStep.step, WithDataInputBlockFailedStep(failedInputs, failedStep.errors))
                  (wrappedLogStack, Left(artificialFailedStep))
              }
              (initialRunState.mergeNested(inputsState, logStack), res)
          }
        }
      )
  }
}

case class WithDataInputBlockFailedStep(failedInputs: List[(String, String)], errors: NonEmptyList[CornichonError]) extends CornichonError {
  val baseErrorMessage = s"WithDataInput block failed for inputs ${printArrowPairs(failedInputs)}"
  override val causedBy = errors.toList
}
