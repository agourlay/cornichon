package com.github.agourlay.cornichon.steps.wrapped

import java.util.concurrent.atomic.AtomicInteger

import com.github.agourlay.cornichon.core.{ Scenario, ScenarioRunner, ScenarioTitleLogInstruction, Session }
import com.github.agourlay.cornichon.steps.cats.EffectStep
import com.github.agourlay.cornichon.testHelpers.CommonTestSuite

import utest._

object AttachStepSpec extends TestSuite with CommonTestSuite {

  val tests = Tests {
    test("merges nested steps in the parent flow when first") {
      val nested = List.fill(5)(alwaysValidAssertStep)
      val steps = AttachStep(_ => nested) :: Nil
      val s = Scenario("scenario with Attach", steps)
      val res = awaitTask(ScenarioRunner.runScenario(Session.newEmpty)(s))
      assert(res.isSuccess)
      assert(res.logs.head == ScenarioTitleLogInstruction("Scenario : scenario with Attach", 1))
      assert(res.logs.size == 7)
    }

    test("merges nested steps in the parent flow when nested") {
      val nested = List.fill(5)(alwaysValidAssertStep)
      val steps = AttachStep(_ => nested) :: Nil
      val s = Scenario("scenario with Attach", RepeatStep(steps, 1, None) :: Nil)
      val res = awaitTask(ScenarioRunner.runScenario(Session.newEmpty)(s))
      assert(res.isSuccess)
      assert(res.logs.head == ScenarioTitleLogInstruction("Scenario : scenario with Attach", 1))
      assert(res.logs.size == 9)
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
    }
  }
}
