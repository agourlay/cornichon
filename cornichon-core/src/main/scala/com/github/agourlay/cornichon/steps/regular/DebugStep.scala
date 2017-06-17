package com.github.agourlay.cornichon.steps.regular

import cats.data.NonEmptyList
import monix.execution.Scheduler
import cats.syntax.either._

import scala.concurrent.Future
import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.core.Engine._

import scala.concurrent.duration.Duration

case class DebugStep(message: Session ⇒ Either[CornichonError, String], title: String = "Debug step") extends ValueStep[String] {

  def setTitle(newTitle: String) = copy(title = newTitle)

  override def run(initialRunState: RunState)(implicit scheduler: Scheduler) =
    Future.successful(message(initialRunState.session).leftMap(NonEmptyList.of(_)))

  override def onError(errors: NonEmptyList[CornichonError], initialRunState: RunState) = {
    val debugErrorLogs = errorLogs(title, errors, initialRunState.depth)
    val failedStep = FailedStep(this, errors)
    (debugErrorLogs, failedStep)
  }

  override def onSuccess(result: String, initialRunState: RunState, executionTime: Duration) =
    (Some(DebugLogInstruction(result, initialRunState.depth)), None)
}