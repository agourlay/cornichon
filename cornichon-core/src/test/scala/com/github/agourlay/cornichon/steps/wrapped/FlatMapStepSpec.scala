package com.github.agourlay.cornichon.steps.wrapped

import java.util.concurrent.atomic.AtomicInteger
import com.github.agourlay.cornichon.core.{ Scenario, ScenarioRunner, ScenarioTitleLogInstruction, Session, Step }
import com.github.agourlay.cornichon.steps.cats.EffectStep
import com.github.agourlay.cornichon.steps.regular.assertStep.{ AssertStep, Assertion, GenericEqualityAssertion }
import com.github.agourlay.cornichon.testHelpers.CommonTestSuite
import munit.FunSuite

class FlatMapStepSpec extends FunSuite with CommonTestSuite {

  test("merge nested steps in the parent flow when first") {
    val dummy = alwaysValidAssertStep
    val nested = List.fill(5)(dummy)
    val steps = FlatMapStep(dummy, _ => nested) :: Nil
    val s = Scenario("scenario with FlatMap", steps)
    val res = awaitTask(ScenarioRunner.runScenario(Session.newEmpty)(s))
    assert(res.isSuccess)
    assert(res.logs.head == ScenarioTitleLogInstruction("Scenario : scenario with FlatMap", 1))
    assert(res.logs.size == 8)
  }

  test("shortcuts if starting step fails") {
    val dummy = alwaysValidAssertStep
    val nested = List.fill(5)(dummy)
    val steps = FlatMapStep(AssertStep("always fails", _ => Assertion.failWith("Nop!")), _ => nested) :: Nil
    val s = Scenario("with FlatMap", steps)

    val res = awaitTask(ScenarioRunner.runScenario(Session.newEmpty)(s))
    scenarioFailsWithMessage(res) {
      """Scenario 'with FlatMap' failed:
          |
          |at step:
          |always fails
          |
          |with error(s):
          |Nop!
          |
          |seed for the run was '1'
          |""".stripMargin
    }
  }

  test("propagates session from first step") {
    val e = EffectStep.fromSyncE("set session value", _.session.addValue("my-key", "my-value"))
    val a = AssertStep("check session", sc => Assertion.either(sc.session.get("my-key").map(v => GenericEqualityAssertion(v, "my-value"))))
    val steps = FlatMapStep(e, _ => a :: Nil) :: Nil
    val s = Scenario("scenario with FlatMap", steps)
    val res = awaitTask(ScenarioRunner.runScenario(Session.newEmpty)(s))
    assert(res.isSuccess)
    assert(res.logs.head == ScenarioTitleLogInstruction("Scenario : scenario with FlatMap", 1))
    assert(res.logs.size == 4)
  }

  test("propagates session from first step (2)") {
    val e = EffectStep.fromSyncE("set session value", _.session.addValue("number-sub-steps", "5"))
    def nestedBuilder(s: Session): List[Step] = {
      val nb = s.get("number-sub-steps").valueUnsafe.toInt
      val dummy = alwaysValidAssertStep
      List.fill(nb)(dummy)
    }

    val steps = FlatMapStep(e, nestedBuilder) :: Nil
    val s = Scenario("scenario with FlatMap", steps)
    val res = awaitTask(ScenarioRunner.runScenario(Session.newEmpty)(s))
    assert(res.isSuccess)
    assert(res.logs.head == ScenarioTitleLogInstruction("Scenario : scenario with FlatMap", 1))
    assert(res.logs.size == 8)
  }

  test("merge nested steps in the parent flow when nested") {
    val dummy = alwaysValidAssertStep
    val nested = List.fill(5)(dummy)
    val steps = FlatMapStep(dummy, _ => nested) :: Nil
    val s = Scenario("scenario with FlatMap", RepeatStep(steps, 1, None) :: Nil)
    val res = awaitTask(ScenarioRunner.runScenario(Session.newEmpty)(s))
    assert(res.isSuccess)
    assert(res.logs.head == ScenarioTitleLogInstruction("Scenario : scenario with FlatMap", 1))
    assert(res.logs.size == 10)
  }

  test("run all nested valid effects") {
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
    val attached = FlatMapStep(effect, _ => nestedSteps)

    val s = Scenario("scenario with effects", attached :: effect :: Nil)
    val res = awaitTask(ScenarioRunner.runScenario(Session.newEmpty)(s))
    assert(res.isSuccess)
    assert(uglyCounter.get() == effectNumber + 2)
  }
}
