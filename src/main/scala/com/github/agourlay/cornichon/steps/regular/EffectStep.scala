package com.github.agourlay.cornichon.steps.regular

import cats.data.Xor
import com.github.agourlay.cornichon.core.{ Engine, LogInstruction, Session, Step }

import scala.concurrent.ExecutionContext

case class EffectStep(
    title: String,
    effect: Session ⇒ Session,
    show: Boolean = true
) extends Step {
  def run(engine: Engine, session: Session, logs: Vector[LogInstruction], depth: Int)(implicit ec: ExecutionContext) = {
    val (res, executionTime) = engine.withDuration {
      Xor.catchNonFatal(effect(session)).leftMap(engine.toCornichonError)
    }
    engine.XorToStepReport(this, session, logs, res, title, depth, show, Some(executionTime))
  }
}