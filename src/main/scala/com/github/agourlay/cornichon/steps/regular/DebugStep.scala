package com.github.agourlay.cornichon.steps.regular

import com.github.agourlay.cornichon.core._

import scala.concurrent.ExecutionContext
import scala.util.{ Failure, Success, Try }

case class DebugStep(message: Session ⇒ String) extends Step {
  val title = s"Debug step with message `$message`"

  def run(engine: Engine, session: Session, depth: Int)(implicit ec: ExecutionContext) = {
    Try { message(session) } match {
      case Success(debugMessage) ⇒
        val runLogs = Vector(DebugLogInstruction(message(session), depth))
        SuccessRunSteps(session, runLogs)
      case Failure(e) ⇒
        val runLogs = engine.errorLogs(title, e, depth)
        FailedRunSteps(this, e, runLogs, session)
    }
  }
}