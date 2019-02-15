package com.github.agourlay.cornichon.steps.cats

import cats.data.{ EitherT, NonEmptyList }
import cats.effect.Effect
import cats.syntax.either._
import com.github.agourlay.cornichon.core.Engine.{ errorsToFailureStep, successLog }
import com.github.agourlay.cornichon.core._
import monix.eval.Task
import monix.execution.Scheduler

import scala.concurrent.duration.Duration

case class EffectStep[F[_]: Effect](title: String, effect: Session ⇒ F[Either[CornichonError, Session]], show: Boolean = true) extends SessionValueStep {

  def setTitle(newTitle: String): Step = copy(title = newTitle)

  override def run(initialRunState: RunState): Task[Either[NonEmptyList[CornichonError], Session]] =
    Task.fromEffect(effect(initialRunState.session)).map(_.leftMap(NonEmptyList.one))

  override def onError(errors: NonEmptyList[CornichonError], initialRunState: RunState): (List[LogInstruction], FailedStep) =
    errorsToFailureStep(this, initialRunState.depth, errors)

  override def logOnSuccess(result: Session, initialRunState: RunState, executionTime: Duration): LogInstruction =
    successLog(title, initialRunState.depth, show, executionTime)

}

object EffectStep {

  def fromEitherT[F[_]: Effect](title: String, effect: Session ⇒ EitherT[F, CornichonError, Session], show: Boolean = true): Step = {
    val effectT: Session ⇒ F[Either[CornichonError, Session]] = s ⇒ effect(s).value
    EffectStep(title, effectT, show)
  }

  def fromSync(title: String, effect: Session ⇒ Session, show: Boolean = true): Step = {
    import Scheduler.Implicits.global
    val effectF: Session ⇒ Task[Either[CornichonError, Session]] = s ⇒ Task.now(effect(s).asRight)
    EffectStep(title, effectF, show)
  }

  def fromSyncE(title: String, effect: Session ⇒ Either[CornichonError, Session], show: Boolean = true): Step = {
    import Scheduler.Implicits.global
    val effectF: Session ⇒ Task[Either[CornichonError, Session]] = s ⇒ Task.now(effect(s))
    EffectStep(title, effectF, show)
  }

  def fromAsync[F[_]: Effect](title: String, effect: Session ⇒ F[Session], show: Boolean = true): Step = {
    import Scheduler.Implicits.global
    val effectF: Session ⇒ Task[Either[CornichonError, Session]] = s ⇒ Task.fromEffect(effect(s)).map(Right.apply)
    EffectStep(title, effectF, show)
  }

}
