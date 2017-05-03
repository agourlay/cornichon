package com.github.agourlay.cornichon.steps.regular

import cats.data.NonEmptyList

import monix.execution.Scheduler

import scala.concurrent.Future

import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.core.Done._
import com.github.agourlay.cornichon.core.Engine._

case class DebugStep(message: Session ⇒ Either[CornichonError, String], title: String = "Debug step") extends Step {

  def setTitle(newTitle: String) = copy(title = newTitle)

  override def run(engine: Engine)(initialRunState: RunState)(implicit scheduler: Scheduler) = {
    val (fullLogs, xor) = message(initialRunState.session) match {
      case Right(debugMessage) ⇒
        val runLogs = Vector(DebugLogInstruction(debugMessage, initialRunState.depth))
        (runLogs, rightDone)
      case Left(e) ⇒
        val errors = NonEmptyList.of(e)
        val debugErrorLogs = errorLogs(title, errors, initialRunState.depth)
        val failedStep = FailedStep(this, errors)
        (debugErrorLogs, Left(failedStep))
    }
    Future.successful(initialRunState.appendLogs(fullLogs), xor)
  }
}