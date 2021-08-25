package com.github.agourlay.cornichon.steps.wrapped

import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.steps.regular.assertStep.{ AssertStep, GenericEqualityAssertion }
import com.github.agourlay.cornichon.testHelpers.CommonTestSuite
import munit.FunSuite

import scala.concurrent.duration._

class RepeatDuringStepSpec extends FunSuite with CommonTestSuite {

  test("fails if 'repeatDuring' block contains a failed step") {
    val nested = AssertStep(
      "always fails",
      _ => GenericEqualityAssertion(true, false)
    ) :: Nil
    val repeatDuring = RepeatDuringStep(nested, 5.millis)
    val s = Scenario("scenario with RepeatDuring", repeatDuring :: Nil)
    val res = awaitTask(ScenarioRunner.runScenario(Session.newEmpty)(s))
    scenarioFailsWithMessage(res) {
      """|Scenario 'scenario with RepeatDuring' failed:
           |
           |at step:
           |always fails
           |
           |with error(s):
           |RepeatDuring block failed before '5 milliseconds'
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

  test("repeat steps inside 'repeatDuring' for at least the duration param") {
    val nested = AssertStep(
      "always valid",
      _ => {
        Thread.sleep(1)
        GenericEqualityAssertion(true, true)
      }
    ) :: Nil
    val repeatDuringStep = RepeatDuringStep(nested, 50.millis)
    val s = Scenario("scenario with RepeatDuring", repeatDuringStep :: Nil)
    val (executionTime, res) = awaitTask(ScenarioRunner.runScenario(Session.newEmpty)(s).timed)
    def clue() = s"${executionTime.toMillis} \n ${LogInstruction.renderLogs(res.logs)}"
    if (!res.isSuccess) {
      clue()
    }
    assert(res.isSuccess)
    assert(executionTime.gt(50.millis))
    // empiric values for the upper bound here
    assert(executionTime.lteq(65.millis))
  }

  test("repeats steps inside 'repeatDuring' at least once if they take more time than the duration param") {
    val nested = AssertStep(
      "always valid but slow",
      _ => {
        Thread.sleep(100)
        GenericEqualityAssertion(true, true)
      }
    ) :: Nil
    val repeatDuringStep = RepeatDuringStep(nested, 50.millis)
    val s = Scenario("scenario with RepeatDuring", repeatDuringStep :: Nil)
    val (executionTime, res) = awaitTask(ScenarioRunner.runScenario(Session.newEmpty)(s).timed)
    def clue() = s"${executionTime.toMillis} \n ${LogInstruction.renderLogs(res.logs)}"
    if (!res.isSuccess) {
      clue()
    }
    assert(res.isSuccess)
    assert(executionTime.gt(50.millis))
    // empiric values for the upper bound here
    assert(executionTime.lt(150.millis))
  }
}
