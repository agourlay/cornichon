package com.github.agourlay.cornichon.steps.wrapped

import cats.data.StateT
import cats.effect.IO
import com.github.agourlay.cornichon.core._

// Transparent wrapper - Steps are flatten in the main execution
case class FlatMapStep(started: Step, nestedProducers: Session => List[Step]) extends WrapperStep {

  val title: String = ""

  override val stateUpdate: StepState = StateT { runState =>
    ScenarioRunner.runStepsShortCircuiting(started :: Nil, runState).flatMap {
      case t @ (rs2, res) =>
        if (res.isLeft)
          IO.pure(t)
        else {
          val nestedStep = nestedProducers(rs2.session)
          ScenarioRunner.runStepsShortCircuiting(nestedStep, rs2)
        }
    }
  }

}
