package com.github.agourlay.cornichon.steps.wrapped

import java.util.concurrent.atomic.AtomicInteger

import com.github.agourlay.cornichon.core.{ Scenario, Session }
import com.github.agourlay.cornichon.steps.StepUtilSpec
import com.github.agourlay.cornichon.steps.regular.EffectStep
import com.github.agourlay.cornichon.steps.regular.assertStep.{ AssertStep, Assertion }
import org.scalatest.{ AsyncWordSpec, Matchers }

class AttachStepSpec extends AsyncWordSpec with Matchers with StepUtilSpec {

  "AttachStep" must {
    "merge nested steps in the parent flow" in {
      val nested = List.fill(5)(AssertStep("always true", s ⇒ Assertion.alwaysValid))
      val steps = AttachStep(title = "", nested) :: Nil
      val s = Scenario("scenario with Attach", steps)
      engine.runScenario(Session.newEmpty)(s).map(_.isSuccess should be(true))
    }

    "run all nested valid effects" in {
      val uglyCounter = new AtomicInteger(0)
      val effectNumber = 5
      val effect = EffectStep.fromSync(
        "increment captured counter",
        s ⇒ {
          uglyCounter.incrementAndGet()
          s
        }
      )

      val nestedSteps = List.fill(effectNumber)(effect)
      val attached = AttachStep(title = "", nestedSteps)

      val s = Scenario("scenario with effects", attached :: effect :: Nil)
      engine.runScenario(Session.newEmpty)(s).map { res ⇒
        res.isSuccess should be(true)
        uglyCounter.get() should be(effectNumber + 1)
      }
    }
  }

}
