package com.github.agourlay.cornichon.steps.wrapped

import java.util.concurrent.atomic.AtomicInteger
import com.github.agourlay.cornichon.core.{ Scenario, ScenarioRunner, Session, Step }
import com.github.agourlay.cornichon.steps.cats.EffectStep
import com.github.agourlay.cornichon.testHelpers.CommonTestSuite
import munit.FunSuite

class AttachStepSpec extends FunSuite with CommonTestSuite {

  test("merges nested steps in the parent flow when first") {
    val nested = List.fill(5)(alwaysValidAssertStep)
    val steps = AttachStep(_ => nested) :: Nil
    val s = Scenario("scenario with Attach", steps)
    val res = awaitTask(ScenarioRunner.runScenario(Session.newEmpty)(s))
    assert(res.isSuccess)
    matchLogsWithoutDuration(res.logs) {
      """
          |   Scenario : scenario with Attach
          |      main steps
          |      valid
          |      valid
          |      valid
          |      valid
          |      valid""".stripMargin
    }
  }

  test("merges nested steps in the parent flow when nested") {
    val nested = List.fill(5)(alwaysValidAssertStep)
    val steps = AttachStep(_ => nested) :: Nil
    val s = Scenario("scenario with Attach", RepeatStep(steps, 1, None) :: Nil)
    val res = awaitTask(ScenarioRunner.runScenario(Session.newEmpty)(s))
    assert(res.isSuccess)
    matchLogsWithoutDuration(res.logs) {
      """
          |   Scenario : scenario with Attach
          |      main steps
          |      Repeat block with occurrence '1'
          |         valid
          |         valid
          |         valid
          |         valid
          |         valid
          |      Repeat block with occurrence '1' succeeded""".stripMargin
    }
  }

  test("runs all nested valid effects") {
    val uglyCounter = new AtomicInteger(0)
    val effectNumber = 5
    val effect = EffectStep.fromSync(
      "increment captured counter",
      sc => {
        uglyCounter.incrementAndGet()
        sc.session
      }
    )

    val nestedSteps = List.fill(effectNumber)(effect)
    val attached = AttachStep(_ => nestedSteps)

    val s = Scenario("scenario with effects", attached :: effect :: Nil)
    val res = awaitTask(ScenarioRunner.runScenario(Session.newEmpty)(s))
    assert(res.isSuccess)
    assert(uglyCounter.get() == effectNumber + 1)
    matchLogsWithoutDuration(res.logs) {
      """
          |   Scenario : scenario with effects
          |      main steps
          |      increment captured counter
          |      increment captured counter
          |      increment captured counter
          |      increment captured counter
          |      increment captured counter
          |      increment captured counter""".stripMargin
    }
  }

  test("available bia Step.eval") {
    val steps = Step.eval(alwaysValidAssertStep) :: Nil
    val s = Scenario("scenario with Attach", steps)
    val res = awaitTask(ScenarioRunner.runScenario(Session.newEmpty)(s))
    assert(res.isSuccess)
    matchLogsWithoutDuration(res.logs) {
      """
          |   Scenario : scenario with Attach
          |      main steps
          |      valid""".stripMargin
    }
  }
}
