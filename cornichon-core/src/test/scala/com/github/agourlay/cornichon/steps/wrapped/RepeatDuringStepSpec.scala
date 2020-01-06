package com.github.agourlay.cornichon.steps.wrapped

import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.steps.regular.assertStep.{ AssertStep, GenericEqualityAssertion }
import com.github.agourlay.cornichon.testHelpers.CommonTestSuite

import utest._

import scala.concurrent.duration._

object RepeatDuringStepSpec extends TestSuite with CommonTestSuite {

  val tests = Tests {
    test("fails if 'repeatDuring' block contains a failed step") {
      val nested = AssertStep(
        "always fails",
        _ => GenericEqualityAssertion(true, false)
      ) :: Nil
      val repeatDuring = RepeatDuringStep(nested, 5.millis)
      val s = Scenario("scenario with RepeatDuring", repeatDuring :: Nil)
      val res = awaitTask(ScenarioRunner.runScenario(Session.newEmpty)(s))
      assert(!res.isSuccess)
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
}
