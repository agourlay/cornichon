package com.github.agourlay.cornichon.steps.wrapped

import cats.effect.IO
import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.steps.cats.EffectStep
import com.github.agourlay.cornichon.steps.regular.assertStep.{AssertStep, GenericEqualityAssertion}
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

  test("succeeds with instant step and generous duration") {
    val nested = AssertStep("instant step", _ => GenericEqualityAssertion(true, true)) :: Nil
    val withinStep = WithinStep(nested, 5.seconds)
    val s = Scenario("scenario with Within", withinStep :: Nil)
    val res = awaitIO(ScenarioRunner.runScenario(Session.newEmpty)(s))
    assert(res.isSuccess)
  }

  test("fails if nested step itself fails, regardless of duration") {
    val nested = AssertStep("failing step", _ => GenericEqualityAssertion(true, false)) :: Nil
    val withinStep = WithinStep(nested, 5.seconds)
    val s = Scenario("scenario with Within", withinStep :: Nil)
    val res = awaitIO(ScenarioRunner.runScenario(Session.newEmpty)(s))
    assert(!res.isSuccess)
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

  test("interrupts nested steps when maxDuration is exceeded (does not wait for nested completion)") {
    val maxDuration = 50.millis
    val nestedSleep = 10.seconds
    val nested = EffectStep.fromAsync("long IO.sleep", sc => IO.sleep(nestedSleep).as(sc.session)) :: Nil
    val withinStep = WithinStep(nested, maxDuration)
    val s = Scenario("scenario with Within cancellation", withinStep :: Nil)
    val start = System.nanoTime()
    val res = awaitIO(ScenarioRunner.runScenario(Session.newEmpty)(s))
    val elapsedMs = (System.nanoTime() - start) / 1_000_000
    assert(!res.isSuccess)
    // Should complete near maxDuration, not wait for nestedSleep. Generous slack for CI.
    assert(elapsedMs < 2_000, s"expected Within to interrupt near $maxDuration, scenario took ${elapsedMs}ms")
  }

}
