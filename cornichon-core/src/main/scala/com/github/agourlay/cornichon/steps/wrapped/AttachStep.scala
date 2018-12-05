package com.github.agourlay.cornichon.steps.wrapped

import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.core.core.StepResult

// Transparent Attach has no title - steps are flatten in the main execution
case class AttachStep(nested: List[Step]) extends WrapperStep {

  val title = ""

  override def run(engine: Engine)(initialRunState: RunState): StepResult =
    engine.runSteps(nested, initialRunState)

}
