package com.github.agourlay.cornichon.steps.wrapped

import com.github.agourlay.cornichon.core._

// Transparent wrapper - Steps are flatten in the main execution
case class FlatMapStep(title: String = "", nestedProducer: Session ⇒ List[Step]) extends WrapperStep {

  // remove AttachStep from remainingStep
  override def run(engine: Engine)(initialRunState: RunState) = {
    val nestedStep = nestedProducer(initialRunState.session)
    engine.runSteps(initialRunState.withSteps(nestedStep))
  }

}
