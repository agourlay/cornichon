package com.github.agourlay.cornichon.steps.regular.assertStep

import cats.data._
import cats.data.Validated.Invalid
import cats.syntax.either._
import cats.effect.IO
import com.github.agourlay.cornichon.core.ScenarioRunner._
import com.github.agourlay.cornichon.core._

import scala.concurrent.duration.Duration

case class AssertStep(title: String, action: ScenarioContext => Assertion, show: Boolean = true) extends LogValueStep[Done] {

  def setTitle(newTitle: String): Step = copy(title = newTitle)

  override def runLogValueStep(runState: RunState): IO[Either[NonEmptyList[CornichonError], Done]] =
    IO.interruptible {
      val assertion = action(runState.scenarioContext)
      assertion.validated match {
        case Invalid(e) => e.asLeft
        case _          => Done.rightDone
      }
    }

  override def onError(errors: NonEmptyList[CornichonError], runState: RunState, executionTime: Duration): (LogInstruction, FailedStep) =
    errorsToFailureStep(this, runState.depth, errors, Some(executionTime))

  override def logOnSuccess(result: Done, runState: RunState, executionTime: Duration): LogInstruction =
    successLog(title, runState.depth, show, executionTime)
}