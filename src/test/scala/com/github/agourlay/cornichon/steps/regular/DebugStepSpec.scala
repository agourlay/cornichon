package com.github.agourlay.cornichon.steps.regular

import com.github.agourlay.cornichon.core.{ Scenario, Session }
import com.github.agourlay.cornichon.steps.StepUtilSpec
import org.scalatest.{ Matchers, WordSpec }

class DebugStepSpec extends WordSpec with Matchers with StepUtilSpec {

  "DebugStep" must {
    "return error if a Debug step throw an exception" in {
      val session = Session.newSession
      val step = DebugStep(s ⇒ {
        6 / 0
        "Never gonna read this"
      })
      val s = Scenario("scenario with faulty debug step", Vector(step))
      engine.runScenario(session)(s).isSuccess should be(false)
    }
  }
}
