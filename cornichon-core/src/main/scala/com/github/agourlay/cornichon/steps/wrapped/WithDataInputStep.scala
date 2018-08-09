package com.github.agourlay.cornichon.steps.wrapped

import cats.data.NonEmptyList
import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.json.CornichonJson
import com.github.agourlay.cornichon.core.Engine._
import com.github.agourlay.cornichon.core.Done._
import com.github.agourlay.cornichon.resolver.PlaceholderResolver
import com.github.agourlay.cornichon.util.Timing._
import com.github.agourlay.cornichon.util.Printing._
import monix.eval.Task

case class WithDataInputStep(nested: List[Step], where: String, r: PlaceholderResolver) extends WrapperStep {

  val title = s"With data input block $where"

  override def run(engine: Engine)(initialRunState: RunState) = {

    def runInputs(inputs: List[List[(String, String)]], runState: RunState): Task[(RunState, Either[(List[(String, String)], FailedStep), Done])] = {
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
                runInputs(inputs.tail, runState.appendLogsFrom(filledState))
              }
            )
        }
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
              val (fullLogs, xor) = inputsRes match {
                case Right(_) ⇒
                  val fullLogs = successTitleLog(initialDepth) +: inputsState.logs :+ SuccessLogInstruction("With data input succeeded for all inputs", initialDepth, Some(executionTime))
                  (fullLogs, rightDone)
                case Left((failedInputs, failedStep)) ⇒
                  val fullLogs = failedTitleLog(initialDepth) +: inputsState.logs :+ FailureLogInstruction("With data input failed for one input", initialDepth, Some(executionTime))
                  val artificialFailedStep = FailedStep.fromSingle(failedStep.step, WithDataInputBlockFailedStep(failedInputs, failedStep.errors))
                  (fullLogs, Left(artificialFailedStep))
              }
              (initialRunState.mergeNested(inputsState, fullLogs), xor)
          }
        }
      )
  }
}

case class WithDataInputBlockFailedStep(failedInputs: List[(String, String)], errors: NonEmptyList[CornichonError]) extends CornichonError {
  val baseErrorMessage = s"WithDataInput block failed for inputs ${printArrowPairs(failedInputs)}"
  override val causedBy = errors.toList
}
