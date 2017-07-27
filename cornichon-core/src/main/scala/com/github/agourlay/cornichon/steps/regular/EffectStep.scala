package com.github.agourlay.cornichon.steps.regular

import cats.instances.future._
import cats.data.{ EitherT, NonEmptyList }
import cats.syntax.either._
import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.core.Engine._

import monix.execution.Scheduler

import scala.concurrent.duration.Duration
import scala.concurrent.{ ExecutionContext, Future }

case class EffectStep(title: String, effect: Session ⇒ Future[Either[CornichonError, Session]], show: Boolean = true) extends ValueStep[Session] {

  def setTitle(newTitle: String) = copy(title = newTitle)

  override def run(initialRunState: RunState)(implicit scheduler: Scheduler) =
    effect(initialRunState.session).map(_.leftMap(NonEmptyList.of(_)))

  override def onError(errors: NonEmptyList[CornichonError], initialRunState: RunState) =
    errorsToFailureStep(this, initialRunState.depth, errors)

  override def onSuccess(result: Session, initialRunState: RunState, executionTime: Duration) =
    (successLog(title, initialRunState.depth, show, executionTime), Some(result))

  //Does not propagate the second step title
  def chain(secondEffect: EffectStep)(implicit ec: ExecutionContext) =
    copy(effect = s ⇒ EitherT(effect(s)).flatMap(s2 ⇒ EitherT(secondEffect.effect(s2))).value)

  def chain(chainedEffect: Session ⇒ Future[Either[CornichonError, Session]])(implicit ec: ExecutionContext) =
    copy(effect = s ⇒ EitherT(effect(s)).flatMap(s2 ⇒ EitherT(chainedEffect(s2))).value)

  def chainSyncE(chainedEffect: Session ⇒ Either[CornichonError, Session])(implicit ec: ExecutionContext) =
    copy(effect = s ⇒ EitherT(effect(s)).flatMap(s2 ⇒ EitherT.fromEither(chainedEffect(s2))).value)

  def chainSync(chainedEffect: Session ⇒ Session)(implicit ec: ExecutionContext) =
    copy(effect = s ⇒ EitherT(effect(s)).map(chainedEffect).value)

}

object EffectStep {

  // Throws if empty list
  def chainEffects(effectsStep: Seq[EffectStep])(implicit ec: ExecutionContext): EffectStep =
    effectsStep.reduce((e1, e2) ⇒ e1.chain(e2))

  def fromEitherT(title: String, effect: Session ⇒ EitherT[Future, CornichonError, Session], show: Boolean = true): EffectStep = {
    val effectT: Session ⇒ Future[Either[CornichonError, Session]] = s ⇒ effect(s).value
    EffectStep(title, effectT, show)
  }

  def fromSync(title: String, effect: Session ⇒ Session, show: Boolean = true): EffectStep = {
    val effectF: Session ⇒ Future[Either[CornichonError, Session]] = s ⇒ Future.successful(Right(effect(s)))
    EffectStep(title, effectF, show)
  }

  def fromSyncE(title: String, effect: Session ⇒ Either[CornichonError, Session], show: Boolean = true): EffectStep = {
    val effectF: Session ⇒ Future[Either[CornichonError, Session]] = s ⇒ Future.successful(effect(s))
    EffectStep(title, effectF, show)
  }

  def fromAsync(title: String, effect: Session ⇒ Future[Session], show: Boolean = true)(implicit ec: ExecutionContext): EffectStep = {
    val effectF: Session ⇒ Future[Either[CornichonError, Session]] = s ⇒ effect(s).map(Right(_))
    EffectStep(title, effectF, show)
  }
}