package com.github.agourlay.cornichon.steps.regular

import cats.data.Xor
import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.core.Engine._
import com.github.agourlay.cornichon.util.Timing._

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.control.NonFatal

case class EffectStep(title: String, effect: Session ⇒ Future[Session], show: Boolean = true) extends Step {

  def setTitle(newTitle: String) = copy(title = newTitle)

  override def run(engine: Engine)(initialRunState: RunState)(implicit ec: ExecutionContext) = {
    withDuration {
      effect(initialRunState.session)
        .map(s ⇒ Xor.Right(s))
        .recover {
          case NonFatal(t) ⇒ Xor.Left(CornichonError.fromThrowable(t))
        }
    }.map {
      case (xor, executionTime) ⇒ xorToStepReport(this, xor, initialRunState, show, Some(executionTime))
    }
  }
}