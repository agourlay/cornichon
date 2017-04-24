package com.github.agourlay.cornichon.steps.regular

import com.github.agourlay.cornichon.core.{ Scenario, Session }
import com.github.agourlay.cornichon.steps.StepUtilSpec
import org.scalatest.{ Matchers, AsyncWordSpec }

import scala.concurrent.Future

class EffectStepSpec extends AsyncWordSpec with Matchers with StepUtilSpec {

  "EffectStep" when {
    "Async" must {
      "return error if an Effect step throw an exception" in {
        val session = Session.newEmpty
        val step = EffectStep(title = "buggy effect", s ⇒ Future {
          6 / 0
          Right(s)
        })
        val s = Scenario("scenario with broken effect step", step :: Nil)
        engine.runScenario(session)(s).map(_.isSuccess should be(false))
      }
    }

    "Sync" must {
      "return error if an Effect step throw an exception" in {
        val session = Session.newEmpty
        val step = EffectStep.fromSync(title = "buggy effect", s ⇒ {
          6 / 0
          s
        })
        val s = Scenario("scenario with broken effect step", step :: Nil)
        engine.runScenario(session)(s).map(_.isSuccess should be(false))
      }
    }
  }
}

