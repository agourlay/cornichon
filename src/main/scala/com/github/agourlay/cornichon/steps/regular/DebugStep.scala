package com.github.agourlay.cornichon.steps.regular

import scala.concurrent.ExecutionContext
import scala.util.{ Failure, Success, Try }

import cats.data.Xor._

import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.core.Done._
import com.github.agourlay.cornichon.core.Engine._

case class DebugStep(message: Session ⇒ String) extends Step {
  val title = s"Debug step"

  override def run(engine: Engine)(initialRunState: RunState)(implicit ec: ExecutionContext) = {
    val (fullLogs, xor) = Try {
      message(initialRunState.session)
    } match {
      case Success(debugMessage) ⇒
        val runLogs = Vector(DebugLogInstruction(debugMessage, initialRunState.depth))
        (runLogs, rightDone)
      case Failure(e) ⇒
        val debugErrorLogs = errorLogs(title, e, initialRunState.depth)
        val failedStep = FailedStep.fromThrowable(this, e)
        (debugErrorLogs, left(failedStep))
    }
    (initialRunState.appendLogs(fullLogs), xor)
  }
}