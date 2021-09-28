package com.github.agourlay.cornichon.steps.regular.assertStep

import com.github.agourlay.cornichon.core.{ Scenario, ScenarioRunner, Session }
import com.github.agourlay.cornichon.testHelpers.CommonTestSuite
import munit.FunSuite

class AssertStepSpec extends FunSuite with CommonTestSuite {

  test("fail if instruction throws exception") {
    val step = AssertStep("stupid step", _ => throw new RuntimeException("boom"))
    val s = Scenario("scenario with stupid test", step :: Nil)
    val r = awaitIO(ScenarioRunner.runScenario(Session.newEmpty)(s))
    assert(!r.isSuccess)
  }

  test("fail if instruction is an invalid assertion") {
    val step = AssertStep("stupid step", _ => Assertion.failWith("failed assertion"))
    val s = Scenario("scenario with stupid test", step :: Nil)
    val r = awaitIO(ScenarioRunner.runScenario(Session.newEmpty)(s))
    assert(!r.isSuccess)
  }

  test("success if non equality was expected") {
    val step = AssertStep("non equals step", _ => GenericEqualityAssertion(1, 2, negate = true))
    val s = Scenario("scenario with unresolved", step :: Nil)
    val r = awaitIO(ScenarioRunner.runScenario(Session.newEmpty)(s))
    assert(r.isSuccess)
  }
}
