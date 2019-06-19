package com.github.agourlay.cornichon.steps.regular

import cats.data.NonEmptyList
import cats.syntax.either._

import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.core.Engine._
import monix.eval.Task

import scala.concurrent.duration.Duration

case class DebugStep(title: String, message: Session ⇒ Either[CornichonError, String]) extends LogValueStep[String] {

  def setTitle(newTitle: String): Step = copy(title = newTitle)

  override def runLogValueStep(runState: RunState): Task[Either[NonEmptyList[CornichonError], String]] =
    Task.delay {
      message(runState.session).leftMap(NonEmptyList.one)
    }

  override def onError(errors: NonEmptyList[CornichonError], runState: RunState): (List[LogInstruction], FailedStep) =
    errorsToFailureStep(this, runState.depth, errors)

  override def logOnSuccess(result: String, runState: RunState, executionTime: Duration): LogInstruction =
    DebugLogInstruction(result, runState.depth)
}