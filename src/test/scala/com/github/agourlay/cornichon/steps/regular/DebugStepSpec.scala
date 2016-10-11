package com.github.agourlay.cornichon.steps.regular

import com.github.agourlay.cornichon.core.{ Scenario, Session }
import com.github.agourlay.cornichon.steps.StepUtilSpec
import org.scalatest.{ AsyncWordSpec, Matchers }

class DebugStepSpec extends AsyncWordSpec with Matchers with StepUtilSpec {

  "DebugStep" must {
    "return error if a Debug step throw an exception" in {
      val session = Session.newEmpty
      val step = DebugStep(s ⇒ {
        6 / 0
        "Never gonna read this"
      })
      val s = Scenario("scenario with faulty debug step", Vector(step))
      engine.runScenario(session)(s).map(_.isSuccess should be(false))
    }
  }
}
