package com.github.agourlay.cornichon.steps.regular

import cats.data.NonEmptyList
import cats.syntax.either._
import cats.effect.IO
import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.core.ScenarioRunner._

import scala.concurrent.duration.Duration

case class DebugStep(title: String, message: ScenarioContext => Either[CornichonError, String]) extends LogValueStep[String] {

  def setTitle(newTitle: String): Step = copy(title = newTitle)

  override def runLogValueStep(runState: RunState): IO[Either[NonEmptyList[CornichonError], String]] =
    IO.delay {
      message(runState.scenarioContext).leftMap(NonEmptyList.one)
    }

  override def onError(errors: NonEmptyList[CornichonError], runState: RunState, executionTime: Duration): (LogInstruction, FailedStep) =
    errorsToFailureStep(this, runState.depth, errors, Some(executionTime))

  override def logOnSuccess(result: String, runState: RunState, executionTime: Duration): LogInstruction =
    DebugLogInstruction(result, runState.depth)
}