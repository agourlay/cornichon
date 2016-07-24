package com.github.agourlay.cornichon.steps.regular

import com.github.agourlay.cornichon.core._

import scala.concurrent.ExecutionContext
import scala.util.{ Failure, Success, Try }
import com.github.agourlay.cornichon.core.Engine._

case class DebugStep(message: Session ⇒ String) extends Step {
  val title = s"Debug step"

  def run(engine: Engine, session: Session, depth: Int)(implicit ec: ExecutionContext) = {
    Try { message(session) } match {
      case Success(debugMessage) ⇒
        val runLogs = Vector(DebugLogInstruction(debugMessage, depth))
        SuccessStepsResult(session, runLogs)
      case Failure(e) ⇒
        val runLogs = errorLogs(title, e, depth)
        val failedStep = FailedStep.fromThrowable(this, e)
        FailureStepsResult(failedStep, session, runLogs)
    }
  }
}