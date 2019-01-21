package com.github.agourlay.cornichon.steps.wrapped

import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.core.core.StepResult
import monix.eval.Task

// Transparent wrapper - Steps are flatten in the main execution
case class FlatMapStep(started: Step, nestedProducers: Session ⇒ List[Step]) extends WrapperStep {

  val title: String = ""

  override def run(engine: Engine)(initialRunState: RunState): StepResult =
    engine.runSteps(started :: Nil, initialRunState).flatMap {
      case t @ (rs2, res) ⇒
        if (res.isLeft)
          Task.now(t)
        else {
          val nestedStep = nestedProducers(rs2.session)
          engine.runSteps(nestedStep, rs2)
        }
    }

}
