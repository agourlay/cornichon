package com.github.agourlay.cornichon.steps.wrapped

import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.steps.regular.assertStep.{ AssertStep, GenericEqualityAssertion }
import com.github.agourlay.cornichon.testHelpers.CommonTestSuite
import munit.FunSuite

class RepeatWithStepSpec extends FunSuite with CommonTestSuite {

  test("fails if 'repeat' block contains a failed step") {
    val nested = AssertStep(
      "always fails",
      _ => GenericEqualityAssertion(true, false)
    ) :: Nil
    val repeatStep = RepeatWithStep(nested, List("1", "2", "3"), "index")
    val s = Scenario("with RepeatWith", repeatStep :: Nil)
    val res = awaitIO(ScenarioRunner.runScenario(Session.newEmpty)(s))
    scenarioFailsWithMessage(res) {
      """Scenario 'with RepeatWith' failed:
          |
          |at step:
          |always fails
          |
          |with error(s):
          |RepeatWith block failed for element '1'
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
    val repeatStep = RepeatWithStep(nested, List("1", "2", "3", "4", "5"), "index")
    val s = Scenario("scenario with Repeat", repeatStep :: Nil)
    val res = awaitIO(ScenarioRunner.runScenario(Session.newEmpty)(s))
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
    val repeatStep = RepeatWithStep(nested, List("1", "2", "3", "4", "5"), indexKeyName)
    val s = Scenario("scenario with Repeat", repeatStep :: Nil)
    val res = awaitIO(ScenarioRunner.runScenario(Session.newEmpty)(s))
    assert(res.isSuccess)
    assert(uglyCounter == loop)
  }
}