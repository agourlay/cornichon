package com.github.agourlay.cornichon.steps.wrapped

import cats.data.StateT
import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.core.core.StepState
import monix.eval.Task

// Transparent wrapper - Steps are flatten in the main execution
case class FlatMapStep(started: Step, nestedProducers: Session ⇒ List[Step]) extends WrapperStep {

  val title: String = ""

  override val stateUpdate: StepState = StateT { runState ⇒
    runState.engine.runStepsShortCircuiting(started :: Nil, runState).flatMap {
      case t @ (rs2, res) ⇒
        if (res.isLeft)
          Task.now(t)
        else {
          val nestedStep = nestedProducers(rs2.session)
          runState.engine.runStepsShortCircuiting(nestedStep, rs2)
        }
    }
  }

}
