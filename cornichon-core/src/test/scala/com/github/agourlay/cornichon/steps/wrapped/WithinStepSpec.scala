package com.github.agourlay.cornichon.steps.wrapped

import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.steps.regular.assertStep.{ AssertStep, GenericEqualityAssertion }
import com.github.agourlay.cornichon.testHelpers.CommonTestSuite
import munit.FunSuite

import scala.concurrent.duration._

class WithinStepSpec extends FunSuite with CommonTestSuite {

  test("controls duration of 'within' wrapped steps") {
    val d = 50.millis
    val nested = AssertStep(
      "possible random value step",
      _ => {
        Thread.sleep(10)
        GenericEqualityAssertion(true, true)
      }
    ) :: Nil
    val withinStep = WithinStep(nested, d)
    val s = Scenario("scenario with Within", withinStep :: Nil)
    val res = awaitIO(ScenarioRunner.runScenario(Session.newEmpty)(s))
    assert(res.isSuccess)
  }

  test("fails if duration of 'within' is exceeded") {
    val d = 10.millis
    val nested = AssertStep(
      "possible random value step",
      _ => {
        Thread.sleep(20)
        GenericEqualityAssertion(true, true)
      }
    ) :: Nil
    val withinStep = WithinStep(nested, d)
    val s = Scenario("scenario with Within", withinStep :: Nil)
    val res = awaitIO(ScenarioRunner.runScenario(Session.newEmpty)(s))
    assert(!res.isSuccess)
  }
}
