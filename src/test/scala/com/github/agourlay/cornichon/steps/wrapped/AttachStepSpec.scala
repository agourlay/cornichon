package com.github.agourlay.cornichon.steps.wrapped

import com.github.agourlay.cornichon.core.{ Scenario, Session, Step }
import com.github.agourlay.cornichon.steps.StepUtilSpec
import com.github.agourlay.cornichon.steps.regular.assertStep.{ AssertStep, Assertion, GenericEqualityAssertion }
import org.scalatest.{ AsyncWordSpec, Matchers }

class AttachStepSpec extends AsyncWordSpec with Matchers with StepUtilSpec {

  "AttachStep" must {
    "merge nested steps in the parent flow" in {
      val nested = List.fill(5)(AssertStep("always true", s ⇒ Assertion.alwaysValid))
      val steps = AttachStep(title = "", nested) :: Nil
      val s = Scenario("scenario with Attach", steps)
      engine.runScenario(Session.newEmpty)(s).map(_.isSuccess should be(true))
    }
  }

}
