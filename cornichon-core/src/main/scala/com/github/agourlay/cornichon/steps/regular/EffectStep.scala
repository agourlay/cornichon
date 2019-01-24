package com.github.agourlay.cornichon.steps.regular

import cats.instances.future._
import cats.data.{ EitherT, NonEmptyList }
import cats.syntax.either._
import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.core.Engine._
import monix.eval.Task

import scala.concurrent.duration.Duration
import scala.concurrent.{ ExecutionContext, Future }

case class EffectStep(title: String, effect: Session ⇒ Future[Either[CornichonError, Session]], show: Boolean = true) extends SessionValueStep {

  def setTitle(newTitle: String) = copy(title = newTitle)

  override def run(initialRunState: RunState): Task[Either[NonEmptyList[CornichonError], Session]] =
    Task.deferFuture(effect(initialRunState.session)).map(_.leftMap(NonEmptyList.one))

  override def onError(errors: NonEmptyList[CornichonError], initialRunState: RunState): (List[LogInstruction], FailedStep) =
    errorsToFailureStep(this, initialRunState.depth, errors)

  override def logOnSuccess(result: Session, initialRunState: RunState, executionTime: Duration): LogInstruction =
    successLog(title, initialRunState.depth, show, executionTime)

  @deprecated("chain complete steps using AttachStep/AttachAsStep", "0.17.0")
  def chainSyncE(chainedEffect: Session ⇒ Either[CornichonError, Session])(implicit ec: ExecutionContext): EffectStep =
    copy(effect = s ⇒ EitherT(effect(s)).flatMap(s2 ⇒ EitherT.fromEither(chainedEffect(s2))).value)
}

object EffectStep {

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