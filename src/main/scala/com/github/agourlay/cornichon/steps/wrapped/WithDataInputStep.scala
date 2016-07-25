package com.github.agourlay.cornichon.steps.wrapped

import cats.data.Xor
import cats.data.Xor._

import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.json.CornichonJson
import com.github.agourlay.cornichon.util.Formats
import com.github.agourlay.cornichon.core.Engine._
import com.github.agourlay.cornichon.core.Done._

import scala.annotation.tailrec
import scala.concurrent.ExecutionContext

case class WithDataInputStep(nested: Vector[Step], where: String) extends WrapperStep {

  val title = s"With data input block $where"

  def run(engine: Engine, session: Session, depth: Int)(implicit ec: ExecutionContext) = {

    @tailrec
    def runInputs(inputs: List[List[(String, String)]], accLogs: Vector[LogInstruction], depth: Int): (Session, Vector[LogInstruction], Xor[FailedStep, Done]) = {
      if (inputs.isEmpty) (session, accLogs, rightDone)
      else {
        val currentInputs = inputs.head
        val filledSession = session.addValues(currentInputs)
        val runInfo = InfoLogInstruction(s"Run with inputs ${Formats.displayTuples(currentInputs)}", depth)
        val (newSession, logs, stepsResult) = engine.runSteps(nested, filledSession, Vector(runInfo), depth + 1)
        stepsResult match {
          case Right(done) ⇒
            // Logs are propogated but not the session
            runInputs(inputs.tail, accLogs ++ logs, depth)
          case Left(failedStep) ⇒
            // Prepend previous logs
            (newSession, accLogs ++ logs, left(failedStep))
        }
      }
    }

    Xor.catchNonFatal(CornichonJson.parseDataTable(where)).fold(
      t ⇒ exceptionToFailureStep(this, session, title, depth, CornichonError.fromThrowable(t)),
      parsedTable ⇒ {
        val inputs = parsedTable.map { line ⇒
          line.toList.map { case (key, json) ⇒ (key, CornichonJson.jsonStringValue(json)) }
        }

        val ((newSession, logs, inputsRes), executionTime) = withDuration {
          runInputs(inputs, Vector.empty, depth + 1)
        }

        inputsRes match {
          case Right(done) ⇒
            val fullLogs = successTitleLog(depth) +: logs :+ SuccessLogInstruction(s"With data input succeeded for all inputs", depth, Some(executionTime))
            (newSession, fullLogs, rightDone)
          case Left(failedStep) ⇒
            val fullLogs = failedTitleLog(depth) +: logs :+ FailureLogInstruction(s"With data input failed for one input", depth, Some(executionTime))
            val artificialFailedStep = FailedStep(failedStep.step, RetryMaxBlockReachedLimit)
            (newSession, fullLogs, left(artificialFailedStep))
        }
      }
    )
  }
}
