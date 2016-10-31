package com.github.agourlay.cornichon.steps.wrapped

import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.steps.StepUtilSpec
import com.github.agourlay.cornichon.steps.regular.assertStep.{ AssertStep, GenericAssertion }
import org.scalatest.{ Matchers, AsyncWordSpec }

class RepeatStepSpec extends AsyncWordSpec with Matchers with StepUtilSpec {

  "RepeatStep" must {
    "fail if 'repeat' block contains a failed step" in {
      val nested = AssertStep(
        "always fails",
        s ⇒ GenericAssertion(true, false)
      ) :: Nil
      val repeatStep = RepeatStep(nested, 5)
      val s = Scenario("scenario with Repeat", repeatStep :: Nil)
      engine.runScenario(Session.newEmpty)(s).map(_.isSuccess should be(false))
    }

    "repeat steps inside a 'repeat' block" in {
      var uglyCounter = 0
      val loop = 5
      val nested = AssertStep(
        "increment captured counter",
        s ⇒ {
          uglyCounter = uglyCounter + 1
          GenericAssertion(true, true)
        }
      ) :: Nil
      val repeatStep = RepeatStep(nested, loop)
      val s = Scenario("scenario with Repeat", repeatStep :: Nil)
      engine.runScenario(Session.newEmpty)(s).map { res ⇒
        res.isSuccess should be(true)
        uglyCounter should be(loop)
      }
    }
  }
}
