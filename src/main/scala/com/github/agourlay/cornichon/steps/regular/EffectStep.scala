package com.github.agourlay.cornichon.steps.regular

import cats.data.Xor
import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.core.Engine._
import com.github.agourlay.cornichon.util.Timing._

import scala.concurrent.{ ExecutionContext, Future }

case class EffectStep(title: String, effect: Session ⇒ Session, show: Boolean = true) extends Step {

  def setTitle(newTitle: String) = copy(title = newTitle)

  override def run(engine: Engine)(initialRunState: RunState)(implicit ec: ExecutionContext) = {
    val (res, executionTime) = withDuration {
      Xor.catchNonFatal(effect(initialRunState.session))
        .leftMap(CornichonError.fromThrowable)
    }
    Future.successful(xorToStepReport(this, res, initialRunState, show, Some(executionTime)))
  }
}