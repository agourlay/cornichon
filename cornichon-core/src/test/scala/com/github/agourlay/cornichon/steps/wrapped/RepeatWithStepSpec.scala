package com.github.agourlay.cornichon.steps.wrapped

import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.steps.regular.assertStep.{AssertStep, GenericEqualityAssertion}
import com.github.agourlay.cornichon.testHelpers.{CommonTestSuite, CountingResource}
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

  test("releases each scenario resource acquired inside the block exactly once") {
    val resource = new CountingResource
    val s = Scenario("repeatWith with resource", RepeatWithStep(resource.step :: Nil, List("1", "2", "3", "4"), "index") :: Nil)
    val res = awaitIO(ScenarioRunner.runScenario(Session.newEmpty)(s))
    assert(res.isSuccess)
    assertEquals(resource.acquired.get, 4)
    assertEquals(resource.released.get, 4)
  }

  test("releases each scenario resource acquired before a failing element exactly once") {
    val resource = new CountingResource
    val nested = resource.step :: AssertStep("fails on the third element", sc => GenericEqualityAssertion(true, sc.session.getUnsafe("index") != "3")) :: Nil
    val s = Scenario("repeatWith with resource", RepeatWithStep(nested, List("1", "2", "3", "4"), "index") :: Nil)
    val res = awaitIO(ScenarioRunner.runScenario(Session.newEmpty)(s))
    assert(!res.isSuccess)
    assertEquals(resource.acquired.get, 3)
    assertEquals(resource.released.get, 3)
  }

}
