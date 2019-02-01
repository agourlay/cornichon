package com.github.agourlay.cornichon.steps.regular

import com.github.agourlay.cornichon.core.{ Scenario, Session }
import com.github.agourlay.cornichon.steps.StepUtilSpec
import org.scalatest.{ AsyncWordSpec, Matchers }

class DebugStepSpec extends AsyncWordSpec with Matchers with StepUtilSpec {

  "DebugStep" must {
    "return error if a Debug step throw an exception" in {
      val step = DebugStep("bad debug", _ ⇒ throw new RuntimeException("boom"))
      val s = Scenario("scenario with faulty debug step", step :: Nil)
      engine.runScenario(Session.newEmpty)(s).map(_.isSuccess should be(false))
    }
  }
}
