package com.github.agourlay.cornichon.steps.wrapped

import cats.data.StateT
import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.core.core.StepState
import monix.eval.Task

// Transparent wrapper - Steps are flatten in the main execution
case class FlatMapStep(started: Step, nestedProducers: Session ⇒ List[Step]) extends WrapperStep {

  val title: String = ""

  override def onEngine(engine: Engine): StepState = StateT { initialRunState ⇒
    engine.runStepsShortCircuiting(started :: Nil, initialRunState).flatMap {
      case t @ (rs2, res) ⇒
        if (res.isLeft)
          Task.now(t)
        else {
          val nestedStep = nestedProducers(rs2.session)
          engine.runStepsShortCircuiting(nestedStep, rs2)
        }
    }
  }

}
