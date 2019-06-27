package com.github.agourlay.cornichon.steps.regular.assertStep

import cats.data._
import cats.data.Validated.Invalid
import cats.syntax.either._
import com.github.agourlay.cornichon.core.ScenarioRunner._
import com.github.agourlay.cornichon.core._
import monix.eval.Task

import scala.concurrent.duration.Duration

case class AssertStep(title: String, action: ScenarioContext ⇒ Assertion, show: Boolean = true) extends LogValueStep[Done] {

  def setTitle(newTitle: String) = copy(title = newTitle)

  override def runLogValueStep(runState: RunState): Task[Either[NonEmptyList[CornichonError], Done]] =
    Task.now {
      val assertion = action(runState.scenarioContext)
      assertion.validated match {
        case Invalid(e) ⇒ e.asLeft
        case _          ⇒ Done.rightDone
      }
    }

  override def onError(errors: NonEmptyList[CornichonError], runState: RunState): (List[LogInstruction], FailedStep) =
    errorsToFailureStep(this, runState.depth, errors)

  override def logOnSuccess(result: Done, runState: RunState, executionTime: Duration): LogInstruction =
    successLog(title, runState.depth, show, executionTime)
}