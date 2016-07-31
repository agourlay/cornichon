package com.github.agourlay.cornichon.steps.regular

import cats.data.Xor
import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.core.Engine._
import com.github.agourlay.cornichon.util.Timing._

import scala.concurrent.ExecutionContext

case class EffectStep(
    title: String,
    effect: Session ⇒ Session,
    show: Boolean = true
) extends Step {

  override def run(engine: Engine, initialRunState: RunState)(implicit ec: ExecutionContext) = {
    val (res, executionTime) = withDuration {
      Xor.catchNonFatal(effect(initialRunState.session))
        .leftMap(CornichonError.fromThrowable)
    }
    xorToStepReport(this, res, title, initialRunState, show, Some(executionTime))
  }
}