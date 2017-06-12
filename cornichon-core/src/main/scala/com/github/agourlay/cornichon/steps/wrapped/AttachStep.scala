package com.github.agourlay.cornichon.steps.wrapped

import com.github.agourlay.cornichon.core._

import monix.execution.Scheduler

// Transparent Attach has no title - steps are flatten in the main execution
case class AttachStep(title: String = "", nested: List[Step]) extends WrapperStep {

  // remove AttachStep from remainingStep and prepend nested to remaining steps
  override def run(engine: Engine)(initialRunState: RunState)(implicit scheduler: Scheduler) =
    engine.runSteps(initialRunState.consumCurrentStep.prependSteps(nested))

}
