package com.github.agourlay.cornichon.steps.wrapped

import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.steps.regular.assertStep.{ AssertStep, GenericEqualityAssertion }
import com.github.agourlay.cornichon.testHelpers.CommonSpec

import utest._

object RetryMaxStepSpec extends TestSuite with CommonSpec {

  val tests = Tests {
    test("fails if 'retryMax' block never succeeds") {
      var uglyCounter = 0
      val loop = 10
      val nested = AssertStep(
        "always fails",
        _ => {
          uglyCounter = uglyCounter + 1
          GenericEqualityAssertion(true, false)
        }
      ) :: Nil
      val retryMaxStep = RetryMaxStep(nested, loop)
      val s = Scenario("with RetryMax", retryMaxStep :: Nil)
      val res = awaitTask(ScenarioRunner.runScenario(Session.newEmpty)(s))
      scenarioFailsWithMessage(res) {
        """Scenario 'with RetryMax' failed:
          |
          |at step:
          |always fails
          |
          |with error(s):
          |Retry max block failed '10' times
          |caused by:
          |expected result was:
          |'true'
          |but actual result is:
          |'false'
          |
          |seed for the run was '1'
          |""".stripMargin
      }
      // Initial run + 'loop' retries
      assert(uglyCounter == loop + 1)
    }

    test("repeats 'retryMax' and might succeed later") {
      var uglyCounter = 0
      val max = 10
      val nested = AssertStep(
        "always fails",
        _ => {
          uglyCounter = uglyCounter + 1
          GenericEqualityAssertion(true, uglyCounter == max - 2)
        }
      ) :: Nil
      val retryMaxStep = RetryMaxStep(nested, max)
      val s = Scenario("scenario with RetryMax", retryMaxStep :: Nil)
      val res = awaitTask(ScenarioRunner.runScenario(Session.newEmpty)(s))
      assert(res.isSuccess)
      assert(uglyCounter == max - 2)
    }
  }
}
