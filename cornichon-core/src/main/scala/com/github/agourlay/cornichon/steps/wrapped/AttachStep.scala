package com.github.agourlay.cornichon.steps.wrapped

import cats.data.StateT
import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.core.core.StepState

// Transparent Attach has no title - steps are flatten in the main execution
case class AttachStep(nested: Session ⇒ List[Step]) extends WrapperStep {

  val title = ""

  override val stateUpdate: StepState = StateT { runState ⇒
    val steps = nested(runState.session)
    ScenarioRunner.runStepsShortCircuiting(steps, runState)
  }

}
