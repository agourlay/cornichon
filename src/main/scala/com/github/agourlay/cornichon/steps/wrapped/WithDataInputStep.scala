package com.github.agourlay.cornichon.steps.wrapped

import java.util.Timer

import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.json.CornichonJson
import com.github.agourlay.cornichon.core.Engine._
import com.github.agourlay.cornichon.core.Done._
import com.github.agourlay.cornichon.util.Timing._
import com.github.agourlay.cornichon.util.Instances._
import cats.syntax.either._
import com.github.agourlay.cornichon.util.Instances

import scala.concurrent.{ ExecutionContext, Future }

case class WithDataInputStep(nested: List[Step], where: String) extends WrapperStep {

  val title = s"With data input block $where"

  override def run(engine: Engine)(initialRunState: RunState)(implicit ec: ExecutionContext, timer: Timer) = {

    def runInputs(inputs: List[List[(String, String)]], runState: RunState): Future[(RunState, Either[(List[(String, String)], FailedStep), Done])] = {
      if (inputs.isEmpty) Future.successful(runState, rightDone)
      else {
        val currentInputs = inputs.head
        val runInfo = InfoLogInstruction(s"Run with inputs ${displayStringPairs(currentInputs)}", runState.depth)
        val boostrapFilledInput = runState.withSteps(nested).addToSession(currentInputs).withLog(runInfo).goDeeper
        engine.runSteps(boostrapFilledInput).flatMap {
          case (filledState, stepsResult) ⇒
            stepsResult match {
              case Right(_) ⇒
                // Logs are propogated but not the session
                runInputs(inputs.tail, runState.appendLogsFrom(filledState))
              case Left(failedStep) ⇒
                // Prepend previous logs
                Future.successful((runState.withSession(filledState.session).appendLogsFrom(filledState), Left((currentInputs, failedStep))))
            }
        }
      }
    }

    Either.catchNonFatal(CornichonJson.parseDataTable(where)).fold(
      t ⇒ Future.successful(exceptionToFailureStep(this, initialRunState, CornichonError.fromThrowable(t))),
      parsedTable ⇒ {
        val inputs = parsedTable.map { line ⇒
          line.toList.map { case (key, json) ⇒ (key, CornichonJson.jsonStringValue(json)) }
        }

        withDuration {
          runInputs(inputs, initialRunState.forNestedSteps(nested))
        }.map {
          case (run, executionTime) ⇒

            val (inputsState, inputsRes) = run
            val initialDepth = initialRunState.depth

            val (fullLogs, xor) = inputsRes match {
              case Right(_) ⇒
                val fullLogs = successTitleLog(initialDepth) +: inputsState.logs :+ SuccessLogInstruction(s"With data input succeeded for all inputs", initialDepth, Some(executionTime))
                (fullLogs, rightDone)
              case Left((failedInputs, failedStep)) ⇒
                val fullLogs = failedTitleLog(initialDepth) +: inputsState.logs :+ FailureLogInstruction(s"With data input failed for one input", initialDepth, Some(executionTime))
                val artificialFailedStep = FailedStep(failedStep.step, WithDataInputBlockFailedStep(failedInputs, failedStep.error))
                (fullLogs, Left(artificialFailedStep))
            }

            (initialRunState.withSession(inputsState.session).appendLogs(fullLogs), xor)
        }
      }
    )
  }
}

case class WithDataInputBlockFailedStep(failedInputs: List[(String, String)], error: CornichonError) extends CornichonError {
  val msg = s"WithDataInput block failed for inputs ${Instances.displayStringPairs(failedInputs)} times with error:\n${error.msg}"
}
