package com.github.agourlay.cornichon.steps.wrapped

import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.steps.StepUtilSpec
import com.github.agourlay.cornichon.steps.regular.assertStep.{ AssertStep, GenericEqualityAssertion }
import org.scalatest.{ Matchers, AsyncWordSpec }

class RetryMaxStepSpec extends AsyncWordSpec with Matchers with StepUtilSpec {

  "RetryMaxStep" must {
    "fail if 'retryMax' block never succeeds" in {
      var uglyCounter = 0
      val loop = 10
      val nested = AssertStep(
        "always fails",
        _ ⇒ {
          uglyCounter = uglyCounter + 1
          GenericEqualityAssertion(true, false)
        }
      ) :: Nil
      val retryMaxStep = RetryMaxStep(nested, loop)
      val s = Scenario("scenario with RetryMax", retryMaxStep :: Nil)
      engine.runScenario(Session.newEmpty)(s).map { res ⇒
        res.isSuccess should be(false)
        // Initial run + 'loop' retries
        uglyCounter should be(loop + 1)
      }
    }

    "repeat 'retryMax' and might succeed later" in {
      var uglyCounter = 0
      val max = 10
      val nested = AssertStep(
        "always fails",
        _ ⇒ {
          uglyCounter = uglyCounter + 1
          GenericEqualityAssertion(true, uglyCounter == max - 2)
        }
      ) :: Nil
      val retryMaxStep = RetryMaxStep(nested, max)
      val s = Scenario("scenario with RetryMax", retryMaxStep :: Nil)
      engine.runScenario(Session.newEmpty)(s).map { res ⇒
        res.isSuccess should be(true)
        uglyCounter should be(max - 2)
      }
    }
  }
}
