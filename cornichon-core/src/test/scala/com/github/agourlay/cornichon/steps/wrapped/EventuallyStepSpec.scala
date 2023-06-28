package com.github.agourlay.cornichon.steps.wrapped

import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.steps.regular.assertStep.{ AssertStep, Assertion, GenericEqualityAssertion }
import com.github.agourlay.cornichon.testHelpers.CommonTestSuite
import munit.FunSuite

import scala.concurrent.duration._

class EventuallyStepSpec extends FunSuite with CommonTestSuite {

  test("replays eventually wrapped steps") {
    val eventuallyConf = EventuallyConf(maxTime = 1.seconds, interval = 10.milliseconds)
    val nested = AssertStep(
      "possible random value step",
      _ => GenericEqualityAssertion(scala.util.Random.nextInt(10), 5)
    ) :: Nil

    val steps = EventuallyStep(nested, eventuallyConf) :: Nil
    val s = Scenario("scenario with eventually", steps)
    val (executionTime, res) = awaitIO(ScenarioRunner.runScenario(Session.newEmpty)(s).timed)
    assert(res.isSuccess)
    assert(executionTime.lt(1100.millis))
  }

  test("replays eventually wrapped steps until limit") {
    val eventuallyConf = EventuallyConf(maxTime = 100.milliseconds, interval = 10.milliseconds)
    var counter = 0
    val nested = AssertStep(
      "impossible random value step", _ => {
        counter = counter + 1
        Assertion.failWith("nop!")
      }
    ) :: Nil
    val eventuallyStep = EventuallyStep(nested, eventuallyConf)
    val s = Scenario("scenario with eventually that fails", eventuallyStep :: Nil)
    val (executionTime, r) = awaitIO(ScenarioRunner.runScenario(Session.newEmpty)(s).timed)
    assert(!r.isSuccess)
    assert(counter <= 10) // at most 10*100millis
    assert(executionTime.lt(120.millis))
  }

  test("replays eventually handle hanging wrapped steps") {
    val eventuallyConf = EventuallyConf(maxTime = 100.milliseconds, interval = 10.milliseconds)
    val nested = AssertStep(
      "slow always true step", _ => {
        Thread.sleep(1000)
        Assertion.alwaysValid
      }
    ) :: Nil
    val eventuallyStep = EventuallyStep(nested, eventuallyConf)
    val s = Scenario("scenario with eventually that fails", eventuallyStep :: Nil)
    val (_, rep) = awaitIO(ScenarioRunner.runScenario(Session.newEmpty)(s).timed)

    scenarioFailsWithMessage(rep) {
      """Scenario 'scenario with eventually that fails' failed:
                  |
                  |at step:
                  |Eventually block with maxDuration = 100 milliseconds and interval = 10 milliseconds
                  |
                  |with error(s):
                  |Eventually block is interrupted due to a long period of inactivity
                  |
                  |seed for the run was '1'
                  |""".stripMargin
    }

    matchLogsWithoutDuration(rep.logs) {
      """
                  |   Scenario : scenario with eventually that fails
                  |      main steps
                  |      Eventually block with maxDuration = 100 milliseconds and interval = 10 milliseconds
                  |      Eventually block did not complete in time after having being tried '1' times""".stripMargin
    }
  }

  test("show last error with `discardStateOnError` on success") {
    val eventuallyConf = EventuallyConf(maxTime = 200.millis, interval = 10.milliseconds)
    var counter = 0
    val nested = AssertStep(
      "Fails at first", _ => {
        if (counter < 2) {
          counter += 1
          Assertion.failWith(s"Failing $counter")
        } else
          Assertion.alwaysValid
      }
    ) :: Nil
    val eventuallyStep = EventuallyStep(nested, eventuallyConf)
    val s = Scenario("scenario with different failures", eventuallyStep :: Nil)
    val res = awaitIO(ScenarioRunner.runScenario(Session.newEmpty)(s))
    assert(res.isSuccess)

    matchLogsWithoutDuration(res.logs.dropRight(1)) {
      """
        |   Scenario : scenario with different failures
        |      main steps
        |      Eventually block with maxDuration = 200 milliseconds and interval = 10 milliseconds
        |         Fails at first
        |         *** FAILED ***
        |         Failing 2
        |         Fails at first""".stripMargin
    }
  }

  test("show last error with `discardStateOnError` on error") {
    val eventuallyConf = EventuallyConf(maxTime = 200.millis, interval = 10.milliseconds)
    var counter = 0
    val nested = AssertStep(
      "Fail differently", _ => {
        if (counter == 0 || counter == 1 || counter == 2) {
          counter += 1
          Assertion.failWith(s"Failing $counter")
        } else
          Assertion.failWith("Failing forever")
      }
    ) :: Nil
    val eventuallyStep = EventuallyStep(nested, eventuallyConf)
    val s = Scenario("scenario with different failures", eventuallyStep :: Nil)
    val res = awaitIO(ScenarioRunner.runScenario(Session.newEmpty)(s))
    scenarioFailsWithMessage(res) {
      """Scenario 'scenario with different failures' failed:
        |
        |at step:
        |Fail differently
        |
        |with error(s):
        |Failing forever
        |
        |seed for the run was '1'
        |""".stripMargin
    }

    matchLogsWithoutDuration(res.logs.dropRight(1)) {
      """
        |   Scenario : scenario with different failures
        |      main steps
        |      Eventually block with maxDuration = 200 milliseconds and interval = 10 milliseconds
        |         Fail differently
        |         *** FAILED ***
        |         Failing forever""".stripMargin
    }
  }
}
