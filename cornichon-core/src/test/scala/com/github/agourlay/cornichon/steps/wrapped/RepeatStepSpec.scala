package com.github.agourlay.cornichon.steps.wrapped

import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.steps.StepUtilSpec
import com.github.agourlay.cornichon.steps.regular.assertStep.{ AssertStep, GenericEqualityAssertion }
import com.github.agourlay.cornichon.util.ScenarioMatchers
import utest._

object RepeatStepSpec extends TestSuite with StepUtilSpec with ScenarioMatchers {

  val tests = Tests {
    test("fails if 'repeat' block contains a failed step") {
      val nested = AssertStep(
        "always fails",
        _ => GenericEqualityAssertion(true, false)
      ) :: Nil
      val repeatStep = RepeatStep(nested, 5, None)
      val s = Scenario("with Repeat", repeatStep :: Nil)
      val res = awaitTask(ScenarioRunner.runScenario(Session.newEmpty)(s))
      scenarioFailsWithMessage(res) {
        """Scenario 'with Repeat' failed:
          |
          |at step:
          |always fails
          |
          |with error(s):
          |Repeat block failed at occurrence 0
          |caused by:
          |expected result was:
          |'true'
          |but actual result is:
          |'false'
          |
          |seed for the run was '1'
          |""".stripMargin
      }
    }

    test("repeats steps inside a 'repeat' block") {
      var uglyCounter = 0
      val loop = 5
      val nested = AssertStep(
        "increment captured counter",
        _ => {
          uglyCounter = uglyCounter + 1
          GenericEqualityAssertion(true, true)
        }
      ) :: Nil
      val repeatStep = RepeatStep(nested, loop, None)
      val s = Scenario("scenario with Repeat", repeatStep :: Nil)
      val res = awaitTask(ScenarioRunner.runScenario(Session.newEmpty)(s))
      assert(res.isSuccess)
      assert(uglyCounter == loop)
    }

    test("exposes index in session") {
      var uglyCounter = 0
      val loop = 5
      val indexKeyName = "my-counter"
      val nested = AssertStep(
        "increment captured counter",
        sc => {
          uglyCounter = uglyCounter + 1
          GenericEqualityAssertion(sc.session.getUnsafe(indexKeyName), uglyCounter.toString)
        }
      ) :: Nil
      val repeatStep = RepeatStep(nested, loop, Some(indexKeyName))
      val s = Scenario("scenario with Repeat", repeatStep :: Nil)
      val res = awaitTask(ScenarioRunner.runScenario(Session.newEmpty)(s))
      assert(res.isSuccess)
      assert(uglyCounter == loop)
    }

  }
}
