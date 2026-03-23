package com.github.agourlay.cornichon.steps.wrapped

import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.steps.regular.assertStep.{AssertStep, GenericEqualityAssertion}
import com.github.agourlay.cornichon.steps.cats.EffectStep
import com.github.agourlay.cornichon.testHelpers.CommonTestSuite
import munit.FunSuite
import java.util.concurrent.atomic.AtomicBoolean

class RetryMaxStepSpec extends FunSuite with CommonTestSuite {

  test("fails if 'retryMax' block never succeeds") {
    var uglyCounter = 0
    val loop = 10
    val nested = AssertStep(
      "always fails",
      _ => {
        uglyCounter = uglyCounter + 1
        GenericEqualityAssertion(true, false)
      }
    ) :: Nil
    val retryMaxStep = RetryMaxStep(nested, loop)
    val s = Scenario("with RetryMax", retryMaxStep :: Nil)
    val res = awaitIO(ScenarioRunner.runScenario(Session.newEmpty)(s))
    scenarioFailsWithMessage(res) {
      """Scenario 'with RetryMax' failed:
          |
          |at step:
          |always fails
          |
          |with error(s):
          |Retry max block failed '10' times
          |caused by:
          |expected result was:
          |'true'
          |but actual result is:
          |'false'
          |
          |seed for the run was '1'
          |""".stripMargin
    }
    // Initial run + 'loop' retries
    assert(uglyCounter == loop + 1)
  }

  test("succeeds immediately if step passes on first try") {
    val nested = AssertStep("always succeeds", _ => GenericEqualityAssertion(true, true)) :: Nil
    val retryMaxStep = RetryMaxStep(nested, 5)
    val s = Scenario("with RetryMax", retryMaxStep :: Nil)
    val res = awaitIO(ScenarioRunner.runScenario(Session.newEmpty)(s))
    assert(res.isSuccess)
  }

  test("fails with retryMax = 1 if step always fails") {
    var uglyCounter = 0
    val nested = AssertStep(
      "always fails",
      _ => {
        uglyCounter += 1
        GenericEqualityAssertion(true, false)
      }
    ) :: Nil
    val retryMaxStep = RetryMaxStep(nested, 1)
    val s = Scenario("with RetryMax", retryMaxStep :: Nil)
    val res = awaitIO(ScenarioRunner.runScenario(Session.newEmpty)(s))
    assert(!res.isSuccess)
    // Initial run + 1 retry
    assert(uglyCounter == 2)
  }

  test("repeats 'retryMax' and might succeed later") {
    var uglyCounter = 0
    val max = 10
    val nested = AssertStep(
      "always fails",
      _ => {
        uglyCounter = uglyCounter + 1
        GenericEqualityAssertion(true, uglyCounter == max - 2)
      }
    ) :: Nil
    val retryMaxStep = RetryMaxStep(nested, max)
    val s = Scenario("scenario with RetryMax", retryMaxStep :: Nil)
    val res = awaitIO(ScenarioRunner.runScenario(Session.newEmpty)(s))
    assert(res.isSuccess)
    assert(uglyCounter == max - 2)
  }

  test("propagates cleanup steps registered inside the retryMax block on success") {
    val cleanupRan = new AtomicBoolean(false)
    val resourceStep = ScenarioResourceStep(
      title = "test resource",
      acquire = EffectStep.fromSyncE("acquire", _.session.addValue("resource", "acquired")),
      release = EffectStep.fromSync("release", sc => { cleanupRan.set(true); sc.session })
    )
    val retryMaxStep = RetryMaxStep(resourceStep :: Nil, 1)
    val s = Scenario("retryMax with cleanup", retryMaxStep :: Nil)
    val res = awaitIO(ScenarioRunner.runScenario(Session.newEmpty)(s))
    assert(res.isSuccess)
    assert(cleanupRan.get(), "Cleanup step from inside RetryMax was not executed")
  }

  test("propagates cleanup steps registered inside the retryMax block on retry") {
    val cleanupRan = new AtomicBoolean(false)
    var attempt = 0
    val resourceStep = ScenarioResourceStep(
      title = "test resource",
      acquire = EffectStep.fromSyncE("acquire", _.session.addValue("resource", "acquired")),
      release = EffectStep.fromSync("release", sc => { cleanupRan.set(true); sc.session })
    )
    val failThenSucceed = AssertStep(
      "fail then succeed",
      _ => {
        attempt += 1
        GenericEqualityAssertion(true, attempt > 1)
      }
    )
    val retryMaxStep = RetryMaxStep(resourceStep :: failThenSucceed :: Nil, 3)
    val s = Scenario("retryMax cleanup on retry", retryMaxStep :: Nil)
    val res = awaitIO(ScenarioRunner.runScenario(Session.newEmpty)(s))
    assert(res.isSuccess)
    assert(cleanupRan.get(), "Cleanup step from failed retry inside RetryMax was not executed")
  }

}
