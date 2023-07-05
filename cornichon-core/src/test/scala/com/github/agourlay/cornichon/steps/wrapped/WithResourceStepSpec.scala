package com.github.agourlay.cornichon.steps.wrapped

import com.github.agourlay.cornichon.testHelpers.CommonTestSuite
import com.github.agourlay.cornichon.core.{ Resource, Scenario, ScenarioRunner, Session }
import com.github.agourlay.cornichon.steps.regular.assertStep.{ AssertStep, Assertion }
import munit.FunSuite

class WithResourceStepSpec extends FunSuite with CommonTestSuite {

  test("succeed if acquire + nested + release are valid") {
    val resource = Resource(
      "super neat resource",
      acquire = AssertStep("Acquire step", _ => Assertion.alwaysValid),
      release = AssertStep("Release step", _ => Assertion.alwaysValid))

    val nested = AssertStep("Nested step", _ => Assertion.alwaysValid) :: Nil
    val withResourceStep = WithResourceStep(nested, resource)
    val s = Scenario("scenario with resource that fails", withResourceStep :: Nil)
    val res = awaitIO(ScenarioRunner.runScenario(Session.newEmpty)(s))
    assert(res.isSuccess)

    matchLogsWithoutDuration(res.logs) {
      """
        |   Scenario : scenario with resource that fails
        |      main steps
        |      With resource block `super neat resource`
        |         Acquire step
        |            Nested step
        |         Release step
        |      With resource block succeeded""".stripMargin
    }
  }

  test("fails if acquire fails") {
    val resource = Resource(
      "super neat resource",
      acquire = AssertStep("Acquire step", _ => Assertion.failWith("Something went wrong")),
      release = AssertStep("Release step", _ => Assertion.alwaysValid))

    val nested = AssertStep("Nested step", _ => Assertion.alwaysValid) :: Nil
    val withResourceStep = WithResourceStep(nested, resource)
    val s = Scenario("scenario with resource that fails", withResourceStep :: Nil)
    val res = awaitIO(ScenarioRunner.runScenario(Session.newEmpty)(s))
    scenarioFailsWithMessage(res) {
      """Scenario 'scenario with resource that fails' failed:
        |
        |at step:
        |Acquire step
        |
        |with error(s):
        |Something went wrong
        |
        |seed for the run was '1'
        |""".stripMargin
    }

    matchLogsWithoutDuration(res.logs) {
      """
        |   Scenario : scenario with resource that fails
        |      main steps
        |      With resource block `super neat resource`
        |         Acquire step
        |         *** FAILED ***
        |         Something went wrong
        |      With resource block failed due to acquire step""".stripMargin
    }
  }

  test("fails if nested fails") {
    val resource = Resource(
      "super neat resource",
      acquire = AssertStep("Acquire step", _ => Assertion.alwaysValid),
      release = AssertStep("Release step", _ => Assertion.alwaysValid))

    val nested = AssertStep("Nested step", _ => Assertion.failWith("Something went wrong")) :: Nil
    val withResourceStep = WithResourceStep(nested, resource)
    val s = Scenario("scenario with resource that fails", withResourceStep :: Nil)
    val res = awaitIO(ScenarioRunner.runScenario(Session.newEmpty)(s))
    scenarioFailsWithMessage(res) {
      """Scenario 'scenario with resource that fails' failed:
        |
        |at step:
        |Nested step
        |
        |with error(s):
        |Something went wrong
        |
        |seed for the run was '1'
        |""".stripMargin
    }

    matchLogsWithoutDuration(res.logs) {
      """
        |   Scenario : scenario with resource that fails
        |      main steps
        |      With resource block `super neat resource`
        |         Acquire step
        |            Nested step
        |            *** FAILED ***
        |            Something went wrong
        |         Release step
        |      With resource block failed""".stripMargin
    }
  }

  test("fails if release fails") {
    val resource = Resource(
      "super neat resource",
      acquire = AssertStep("Acquire step", _ => Assertion.alwaysValid),
      release = AssertStep("Release step", _ => Assertion.failWith("Something went wrong")))

    val nested = AssertStep("Nested step", _ => Assertion.alwaysValid) :: Nil
    val withResourceStep = WithResourceStep(nested, resource)
    val s = Scenario("scenario with resource that fails", withResourceStep :: Nil)
    val res = awaitIO(ScenarioRunner.runScenario(Session.newEmpty)(s))
    scenarioFailsWithMessage(res) {
      """Scenario 'scenario with resource that fails' failed:
        |
        |at step:
        |Release step
        |
        |with error(s):
        |Something went wrong
        |
        |seed for the run was '1'
        |""".stripMargin
    }

    matchLogsWithoutDuration(res.logs) {
      """
        |   Scenario : scenario with resource that fails
        |      main steps
        |      With resource block `super neat resource`
        |         Acquire step
        |            Nested step
        |         Release step
        |         *** FAILED ***
        |         Something went wrong
        |      With resource block failed due to release step""".stripMargin
    }
  }
}
