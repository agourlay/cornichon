package com.github.agourlay.cornichon.steps.wrapped

import com.github.agourlay.cornichon.core.{ Engine, Session, Step, WrapperStep }

import scala.concurrent.ExecutionContext

// Transparent Attach has no title - steps are flatten in the main execution
case class AttachStep(title: String = "", nested: Vector[Step]) extends WrapperStep {

  def run(engine: Engine, session: Session, depth: Int)(implicit ec: ExecutionContext) =
    engine.runSteps(nested, session, Vector.empty, depth)

}
