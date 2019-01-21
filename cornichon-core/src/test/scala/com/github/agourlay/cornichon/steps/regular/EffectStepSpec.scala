package com.github.agourlay.cornichon.steps.regular

import com.github.agourlay.cornichon.core.{ Scenario, Session }
import com.github.agourlay.cornichon.steps.StepUtilSpec
import org.scalatest.{ Matchers, AsyncWordSpec }

import scala.concurrent.Future

class EffectStepSpec extends AsyncWordSpec with Matchers with StepUtilSpec {

  "EffectStep" when {
    "Async" must {
      "return error if an Effect step throw an exception" in {
        val step = EffectStep(title = "buggy effect", _ ⇒ Future { throw new RuntimeException("boom") })
        val s = Scenario("scenario with broken effect step", step :: Nil)
        engine.runScenario(Session.newEmpty)(s).map(_.isSuccess should be(false))
      }
    }

    "Sync" must {
      "return error if an Effect step throw an exception" in {
        val step = EffectStep.fromSync(title = "buggy effect", _ ⇒ throw new RuntimeException("boom"))
        val s = Scenario("scenario with broken effect step", step :: Nil)
        engine.runScenario(Session.newEmpty)(s).map(_.isSuccess should be(false))
      }
    }
  }
}

