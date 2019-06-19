package com.github.agourlay.cornichon.steps.regular.assertStep

import cats.data._
import cats.data.Validated.Invalid
import cats.syntax.either._
import com.github.agourlay.cornichon.core.Engine._
import com.github.agourlay.cornichon.core._
import monix.eval.Task

import scala.concurrent.duration.Duration

case class AssertStep(title: String, action: Session ⇒ Assertion, show: Boolean = true) extends LogValueStep[Done] {

  def setTitle(newTitle: String) = copy(title = newTitle)

  override def runLogValueStep(runState: RunState): Task[Either[NonEmptyList[CornichonError], Done]] =
    Task.now {
      val assertion = action(runState.session)
      assertion.validated match {
        case Invalid(e) ⇒ e.asLeft
        case _          ⇒ Done.rightDone
      }
    }

  override def onError(errors: NonEmptyList[CornichonError], initialRunState: RunState): (List[LogInstruction], FailedStep) =
    errorsToFailureStep(this, initialRunState.depth, errors)

  override def logOnSuccess(result: Done, initialRunState: RunState, executionTime: Duration): LogInstruction =
    successLog(title, initialRunState.depth, show, executionTime)
}