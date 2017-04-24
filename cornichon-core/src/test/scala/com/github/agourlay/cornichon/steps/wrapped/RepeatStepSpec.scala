package com.github.agourlay.cornichon.steps.wrapped

import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.steps.StepUtilSpec
import com.github.agourlay.cornichon.steps.regular.assertStep.{ AssertStep, GenericEqualityAssertion }
import org.scalatest.{ Matchers, AsyncWordSpec }

class RepeatStepSpec extends AsyncWordSpec with Matchers with StepUtilSpec {

  "RepeatStep" must {
    "fail if 'repeat' block contains a failed step" in {
      val nested = AssertStep(
        "always fails",
        s ⇒ GenericEqualityAssertion(true, false)
      ) :: Nil
      val repeatStep = RepeatStep(nested, 5, None)
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
          GenericEqualityAssertion(true, true)
        }
      ) :: Nil
      val repeatStep = RepeatStep(nested, loop, None)
      val s = Scenario("scenario with Repeat", repeatStep :: Nil)
      engine.runScenario(Session.newEmpty)(s).map { res ⇒
        res.isSuccess should be(true)
        uglyCounter should be(loop)
      }
    }

    "expose indice in session" in {
      var uglyCounter = 0
      val loop = 5
      val indiceKeyName = "my-counter"
      val nested = AssertStep(
        "increment captured counter",
        s ⇒ {
          uglyCounter = uglyCounter + 1
          GenericEqualityAssertion(s.getUnsafe(indiceKeyName), uglyCounter.toString)
        }
      ) :: Nil
      val repeatStep = RepeatStep(nested, loop, Some(indiceKeyName))
      val s = Scenario("scenario with Repeat", repeatStep :: Nil)
      engine.runScenario(Session.newEmpty)(s).map { res ⇒
        res.isSuccess should be(true)
        uglyCounter should be(loop)
      }
    }
  }
}
