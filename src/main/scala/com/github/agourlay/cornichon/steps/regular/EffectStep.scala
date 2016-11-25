package com.github.agourlay.cornichon.steps.regular

import java.util.Timer

import cats.data.NonEmptyList
import cats.syntax.either._
import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.core.Engine._
import com.github.agourlay.cornichon.util.Timing._

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.control.NonFatal

case class EffectStep(title: String, effect: Session ⇒ Future[Session], show: Boolean = true) extends Step {

  def setTitle(newTitle: String) = copy(title = newTitle)

  override def run(engine: Engine)(initialRunState: RunState)(implicit ec: ExecutionContext, timer: Timer) = {
    withDuration {
      Either
        .catchNonFatal(effect(initialRunState.session))
        .leftMap(e ⇒ NonEmptyList.of(CornichonError.fromThrowable(e)))
        .fold(
          errors ⇒ Future.successful(Left(errors)),
          fs ⇒ fs.map(Right(_)).recover { case NonFatal(t) ⇒ Left(NonEmptyList.of(CornichonError.fromThrowable(t))) }
        )
    }.map {
      case (xor, executionTime) ⇒ xorToStepReport(this, xor, initialRunState, show, Some(executionTime))
    }
  }
}

object EffectStep {
  def fromSync(title: String, effect: Session ⇒ Session, show: Boolean = true): EffectStep = {
    val effectF: Session ⇒ Future[Session] = s ⇒ Future.successful(effect(s))
    EffectStep(title, effectF, show)
  }
}