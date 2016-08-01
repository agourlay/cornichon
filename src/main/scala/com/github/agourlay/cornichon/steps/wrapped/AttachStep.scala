package com.github.agourlay.cornichon.steps.wrapped

import com.github.agourlay.cornichon.core._

import scala.concurrent.ExecutionContext

// Transparent Attach has no title - steps are flatten in the main execution
case class AttachStep(title: String = "", nested: Vector[Step]) extends WrapperStep {

  override def run(engine: Engine)(initialRunState: RunState)(implicit ec: ExecutionContext) =
    // remove AttachStep from remainingStep and prepend nested to remaing steps
    engine.runSteps(initialRunState.consumCurrentStep.prependSteps(nested))

}
