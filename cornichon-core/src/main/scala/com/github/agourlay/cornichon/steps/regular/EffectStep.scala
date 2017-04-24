package com.github.agourlay.cornichon.steps.regular

import akka.actor.Scheduler
import cats.data.NonEmptyList
import cats.syntax.either._
import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.core.Engine._
import com.github.agourlay.cornichon.util.Timing._

import scala.concurrent.{ ExecutionContext, Future }

case class EffectStep(title: String, effect: Session ⇒ Future[Either[CornichonError, Session]], show: Boolean = true) extends Step {

  def setTitle(newTitle: String) = copy(title = newTitle)

  override def run(engine: Engine)(initialRunState: RunState)(implicit ec: ExecutionContext, scheduler: Scheduler) =
    withDuration(effect(initialRunState.session)).map {
      case (xor, executionTime) ⇒ xorToStepReport(this, xor.leftMap(e ⇒ NonEmptyList.of(e)), initialRunState, show, Some(executionTime))
    }
}

object EffectStep {
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