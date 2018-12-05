package com.github.agourlay.cornichon.steps.regular

import cats.data.NonEmptyList
import cats.syntax.either._

import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.core.Engine._
import monix.eval.Task

import scala.concurrent.duration.Duration

case class DebugStep(message: Session ⇒ Either[CornichonError, String], title: String = "Debug step") extends LogValueStep[String] {

  def setTitle(newTitle: String) = copy(title = newTitle)

  override def run(initialRunState: RunState): Task[Either[NonEmptyList[CornichonError], String]] =
    Task.delay {
      message(initialRunState.session).leftMap(NonEmptyList.one)
    }

  override def onError(errors: NonEmptyList[CornichonError], initialRunState: RunState): (List[LogInstruction], FailedStep) =
    errorsToFailureStep(this, initialRunState.depth, errors)

  override def logOnSuccess(result: String, initialRunState: RunState, executionTime: Duration): LogInstruction =
    DebugLogInstruction(result, initialRunState.depth)
}