package com.github.agourlay.cornichon.steps.wrapped

import java.util.concurrent.atomic.AtomicInteger

import com.github.agourlay.cornichon.core.{ Scenario, ScenarioRunner, ScenarioTitleLogInstruction, Session, Step }
import com.github.agourlay.cornichon.steps.StepUtilSpec
import com.github.agourlay.cornichon.steps.cats.EffectStep
import com.github.agourlay.cornichon.steps.regular.assertStep.{ AssertStep, Assertion, GenericEqualityAssertion }
import com.github.agourlay.cornichon.util.ScenarioMatchers
import org.scalatest.{ AsyncWordSpec, Matchers, OptionValues }

class FlatMapStepSpec extends AsyncWordSpec with Matchers with OptionValues with StepUtilSpec with ScenarioMatchers {

  "FlatMapStep" must {
    "merge nested steps in the parent flow when first" in {
      val dummy = AssertStep("always true", _ => Assertion.alwaysValid)
      val nested = List.fill(5)(dummy)
      val steps = FlatMapStep(dummy, _ => nested) :: Nil
      val s = Scenario("scenario with FlatMap", steps)
      ScenarioRunner.runScenario(Session.newEmpty)(s).map { r =>
        r.isSuccess should be(true)
        r.logs.headOption.value should be(ScenarioTitleLogInstruction("Scenario : scenario with FlatMap", 1))
        r.logs.size should be(8)
      }
    }

    "shortcut if starting step fails" in {
      val dummy = AssertStep("always true", _ => Assertion.alwaysValid)
      val nested = List.fill(5)(dummy)
      val steps = FlatMapStep(AssertStep("always fails", _ => Assertion.failWith("Nop!")), _ => nested) :: Nil
      val s = Scenario("with FlatMap", steps)

      ScenarioRunner.runScenario(Session.newEmpty)(s).map { res =>
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
    }

    "propagate session from first step" in {
      val e = EffectStep.fromSyncE("set session value", _.session.addValue("my-key", "my-value"))
      val a = AssertStep("check session", sc => Assertion.either(sc.session.get("my-key").map(v => GenericEqualityAssertion(v, "my-value"))))
      val steps = FlatMapStep(e, _ => a :: Nil) :: Nil
      val s = Scenario("scenario with FlatMap", steps)
      ScenarioRunner.runScenario(Session.newEmpty)(s).map { r =>
        r.isSuccess should be(true)
        r.logs.headOption.value should be(ScenarioTitleLogInstruction("Scenario : scenario with FlatMap", 1))
        r.logs.size should be(4)
      }
    }

    "propagate session from first step (2)" in {
      val e = EffectStep.fromSyncE("set session value", _.session.addValue("number-sub-steps", "5"))
      def nestedBuilder(s: Session): List[Step] = {
        val nb = s.get("number-sub-steps").valueUnsafe.toInt
        val dummy = AssertStep("always true", _ => Assertion.alwaysValid)
        List.fill(nb)(dummy)
      }

      val steps = FlatMapStep(e, nestedBuilder) :: Nil
      val s = Scenario("scenario with FlatMap", steps)
      ScenarioRunner.runScenario(Session.newEmpty)(s).map { r =>
        r.isSuccess should be(true)
        r.logs.headOption.value should be(ScenarioTitleLogInstruction("Scenario : scenario with FlatMap", 1))
        r.logs.size should be(8)
      }
    }

    "merge nested steps in the parent flow when nested" in {
      val dummy = AssertStep("always true", _ => Assertion.alwaysValid)
      val nested = List.fill(5)(dummy)
      val steps = FlatMapStep(dummy, _ => nested) :: Nil
      val s = Scenario("scenario with FlatMap", RepeatStep(steps, 1, None) :: Nil)
      ScenarioRunner.runScenario(Session.newEmpty)(s).map { r =>
        r.isSuccess should be(true)
        r.logs.headOption.value should be(ScenarioTitleLogInstruction("Scenario : scenario with FlatMap", 1))
        r.logs.size should be(10)
      }
    }

    "run all nested valid effects" in {
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
      ScenarioRunner.runScenario(Session.newEmpty)(s).map { res =>
        res.isSuccess should be(true)
        uglyCounter.get() should be(effectNumber + 2)
      }
    }
  }

}
