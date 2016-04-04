package com.github.agourlay.cornichon.steps.regular

import cats.data.Xor
import com.github.agourlay.cornichon.core._

import scala.concurrent.ExecutionContext

case class AssertStep[A](
    title: String,
    action: Session ⇒ StepAssertion[A],
    negate: Boolean = false,
    show: Boolean = true
) extends Step {
  def run(engine: Engine, session: Session, logs: Vector[LogInstruction], depth: Int)(implicit ec: ExecutionContext) = {
    val res = Xor.catchNonFatal(action(session))
      .leftMap(engine.toCornichonError)
      .flatMap { assertion ⇒
        engine.runStepPredicate(negate, session, assertion)
      }
    engine.XorToStepReport(this, session, Vector.empty, res, title, depth, show)
  }
}
