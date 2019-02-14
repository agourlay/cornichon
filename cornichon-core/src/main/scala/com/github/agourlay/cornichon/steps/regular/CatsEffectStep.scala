package com.github.agourlay.cornichon.steps.regular

import cats.data.NonEmptyList
import cats.effect.Effect
import cats.syntax.either._
import com.github.agourlay.cornichon.core.Engine.{ errorsToFailureStep, successLog }
import com.github.agourlay.cornichon.core._
import monix.eval.Task

import scala.concurrent.duration.Duration

case class CatsEffectStep[F[_]: Effect](title: String, effect: Session ⇒ F[Either[CornichonError, Session]], show: Boolean = true) extends SessionValueStep {

  def setTitle(newTitle: String): Step = copy(title = newTitle)

  override def run(initialRunState: RunState): Task[Either[NonEmptyList[CornichonError], Session]] = {
    Task.fromEffect(effect(initialRunState.session)).map(_.leftMap(NonEmptyList.one))
  }

  override def onError(errors: NonEmptyList[CornichonError], initialRunState: RunState): (List[LogInstruction], FailedStep) =
    errorsToFailureStep(this, initialRunState.depth, errors)

  override def logOnSuccess(result: Session, initialRunState: RunState, executionTime: Duration): LogInstruction =
    successLog(title, initialRunState.depth, show, executionTime)

}
