package com.github.agourlay.cornichon.steps.wrapped

import cats.data.StateT
import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.core.core.StepState

// Transparent Attach has no title - steps are flatten in the main execution
case class AttachStep(nested: Session ⇒ List[Step]) extends WrapperStep {

  val title = ""

  override def onEngine(engine: Engine): StepState = StateT { initialRunState ⇒
    val steps = nested(initialRunState.session)
    engine.runStepsShortCircuiting(steps, initialRunState)
  }

}
