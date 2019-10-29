package com.github.agourlay.cornichon.steps.regular

import cats.data.{ EitherT, NonEmptyList }
import cats.syntax.either._
import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.core.ScenarioRunner._
import monix.eval.Task

import scala.concurrent.duration.Duration
import scala.concurrent.{ ExecutionContext, Future }

case class EffectStep(title: String, effect: ScenarioContext => Future[Either[CornichonError, Session]], show: Boolean = true) extends SessionValueStep {

  def setTitle(newTitle: String) = copy(title = newTitle)

  override def runSessionValueStep(runState: RunState): Task[Either[NonEmptyList[CornichonError], Session]] =
    Task.deferFuture(effect(runState.scenarioContext)).map(_.leftMap(NonEmptyList.one))

  override def onError(errors: NonEmptyList[CornichonError], runState: RunState, executionTime: Duration): (LogInstruction, FailedStep) =
    errorsToFailureStep(this, runState.depth, errors, Some(executionTime))

  override def logOnSuccess(result: Session, runState: RunState, executionTime: Duration): LogInstruction =
    successLog(title, runState.depth, show, executionTime)
}

object EffectStep {

  def fromEitherT(title: String, effect: ScenarioContext => EitherT[Future, CornichonError, Session], show: Boolean = true): EffectStep = {
    val effectT: ScenarioContext => Future[Either[CornichonError, Session]] = s => effect(s).value
    EffectStep(title, effectT, show)
  }

  def fromSync(title: String, effect: ScenarioContext => Session, show: Boolean = true): EffectStep = {
    val effectF: ScenarioContext => Future[Either[CornichonError, Session]] = s => Future.successful(effect(s).asRight)
    EffectStep(title, effectF, show)
  }

  def fromSyncE(title: String, effect: ScenarioContext => Either[CornichonError, Session], show: Boolean = true): EffectStep = {
    val effectF: ScenarioContext => Future[Either[CornichonError, Session]] = s => Future.successful(effect(s))
    EffectStep(title, effectF, show)
  }

  def fromAsync(title: String, effect: ScenarioContext => Future[Session], show: Boolean = true)(implicit ec: ExecutionContext): EffectStep = {
    val effectF: ScenarioContext => Future[Either[CornichonError, Session]] = s => effect(s).map(Right.apply)
    EffectStep(title, effectF, show)
  }
}