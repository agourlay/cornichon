package com.github.agourlay.cornichon.steps.regular

import com.github.agourlay.cornichon.core._

import scala.concurrent.ExecutionContext
import scala.util.{ Failure, Success, Try }

case class DebugStep(message: Session ⇒ String) extends Step {
  val title = s"Debug step with message `$message`"

  def run(engine: Engine, session: Session, logs: Vector[LogInstruction], depth: Int)(implicit ec: ExecutionContext) = {
    Try { message(session) } match {
      case Success(debugMessage) ⇒
        val updatedLogs = logs :+ InfoLogInstruction(message(session), depth)
        SuccessRunSteps(session, updatedLogs)
      case Failure(e) ⇒
        val updatedLogs = logs ++ engine.errorLogs(title, e, depth, nextSteps)
        FailedRunSteps(this, Vector.empty, updatedLogs, session)
    }
  }
}