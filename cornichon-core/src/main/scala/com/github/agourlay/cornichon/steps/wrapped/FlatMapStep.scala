package com.github.agourlay.cornichon.steps.wrapped

import com.github.agourlay.cornichon.core._
import monix.eval.Task

// Transparent wrapper - Steps are flatten in the main execution
case class FlatMapStep(started: Step, nestedProducers: Session ⇒ List[Step], title: String = "") extends WrapperStep {

  override def run(engine: Engine)(initialRunState: RunState) =
    engine.runSteps(started :: Nil, initialRunState).flatMap {
      case (rs2, res) ⇒
        if (res.isLeft)
          Task.delay((rs2, res))
        else {
          val nestedStep = nestedProducers(rs2.session)
          engine.runSteps(nestedStep, rs2)
        }
    }

}
