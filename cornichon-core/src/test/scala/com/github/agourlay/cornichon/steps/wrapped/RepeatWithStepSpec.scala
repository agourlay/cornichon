package com.github.agourlay.cornichon.steps.wrapped

import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.steps.StepUtilSpec
import com.github.agourlay.cornichon.steps.regular.assertStep.{ AssertStep, GenericEqualityAssertion }
import org.scalatest.{ Matchers, AsyncWordSpec }

class RepeatWithStepSpec extends AsyncWordSpec with Matchers with StepUtilSpec {

  "RepeatWithStep" must {
    "fail if 'repeat' block contains a failed step" in {
      val nested = AssertStep(
        "always fails",
        _ ⇒ GenericEqualityAssertion(true, false)
      ) :: Nil
      val repeatStep = RepeatWithStep(nested, List("1", "2", "3"), "index")
      val s = Scenario("scenario with Repeat", repeatStep :: Nil)
      ScenarioRunner.runScenario(Session.newEmpty)(s).map(_.isSuccess should be(false))
    }

    "repeat steps inside a 'repeat' block" in {
      var uglyCounter = 0
      val loop = 5
      val nested = AssertStep(
        "increment captured counter",
        _ ⇒ {
          uglyCounter = uglyCounter + 1
          GenericEqualityAssertion(true, true)
        }
      ) :: Nil
      val repeatStep = RepeatWithStep(nested, List("1", "2", "3", "4", "5"), "index")
      val s = Scenario("scenario with Repeat", repeatStep :: Nil)
      ScenarioRunner.runScenario(Session.newEmpty)(s).map { res ⇒
        res.isSuccess should be(true)
        uglyCounter should be(loop)
      }
    }

    "expose index in session" in {
      var uglyCounter = 0
      val loop = 5
      val indexKeyName = "my-counter"
      val nested = AssertStep(
        "increment captured counter",
        sc ⇒ {
          uglyCounter = uglyCounter + 1
          GenericEqualityAssertion(sc.session.getUnsafe(indexKeyName), uglyCounter.toString)
        }
      ) :: Nil
      val repeatStep = RepeatWithStep(nested, List("1", "2", "3", "4", "5"), indexKeyName)
      val s = Scenario("scenario with Repeat", repeatStep :: Nil)
      ScenarioRunner.runScenario(Session.newEmpty)(s).map { res ⇒
        res.isSuccess should be(true)
        uglyCounter should be(loop)
      }
    }
  }
}
