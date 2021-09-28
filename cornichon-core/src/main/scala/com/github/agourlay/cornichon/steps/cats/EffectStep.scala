package com.github.agourlay.cornichon.steps.cats

import cats.data.{ EitherT, NonEmptyList }
import cats.effect.IO
import cats.syntax.either._
import com.github.agourlay.cornichon.core.ScenarioRunner.{ errorsToFailureStep, successLog }
import com.github.agourlay.cornichon.core._

import scala.concurrent.duration.Duration

case class EffectStep(title: String, effect: ScenarioContext => IO[Either[CornichonError, Session]], show: Boolean = true) extends SessionValueStep {

  def setTitle(newTitle: String): Step = copy(title = newTitle)

  override def runSessionValueStep(runState: RunState): IO[Either[NonEmptyList[CornichonError], Session]] =
    effect(runState.scenarioContext).map(_.leftMap(NonEmptyList.one))

  override def onError(errors: NonEmptyList[CornichonError], runState: RunState, executionTime: Duration): (LogInstruction, FailedStep) =
    errorsToFailureStep(this, runState.depth, errors, Some(executionTime))

  override def logOnSuccess(result: Session, runState: RunState, executionTime: Duration): LogInstruction =
    successLog(title, runState.depth, show, executionTime)

}

object EffectStep {

  def fromEitherT(title: String, effect: ScenarioContext => EitherT[IO, CornichonError, Session], show: Boolean = true): Step = {
    val effectT: ScenarioContext => IO[Either[CornichonError, Session]] = s => effect(s).value
    EffectStep(title, effectT, show)
  }

  def fromSync(title: String, effect: ScenarioContext => Session, show: Boolean = true): Step = {
    val effectF: ScenarioContext => IO[Either[CornichonError, Session]] = s => IO.pure(effect(s).asRight)
    EffectStep(title, effectF, show)
  }

  def fromSyncE(title: String, effect: ScenarioContext => Either[CornichonError, Session], show: Boolean = true): Step = {
    val effectF: ScenarioContext => IO[Either[CornichonError, Session]] = s => IO.pure(effect(s))
    EffectStep(title, effectF, show)
  }

  def fromAsync(title: String, effect: ScenarioContext => IO[Session], show: Boolean = true): Step = {
    val effectF: ScenarioContext => IO[Either[CornichonError, Session]] = s => effect(s).map(Right.apply)
    EffectStep(title, effectF, show)
  }

}
