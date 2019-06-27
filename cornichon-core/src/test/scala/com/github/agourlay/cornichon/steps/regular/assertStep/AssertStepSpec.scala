package com.github.agourlay.cornichon.steps.regular.assertStep

import cats.scalatest.EitherValues
import com.github.agourlay.cornichon.core.{ Scenario, ScenarioRunner, Session }
import com.github.agourlay.cornichon.steps.StepUtilSpec
import org.scalatest.{ AsyncWordSpec, Matchers }

class AssertStepSpec extends AsyncWordSpec
  with Matchers
  with EitherValues
  with StepUtilSpec {

  "AssertStep" must {

    "fail if instruction throws exception" in {
      val step = AssertStep("stupid step", _ ⇒ throw new RuntimeException("boom"))
      val s = Scenario("scenario with stupid test", step :: Nil)
      ScenarioRunner.runScenario(Session.newEmpty)(s).map(_.isSuccess should be(false))
    }

    "success if non equality was expected" in {
      val step = AssertStep("non equals step", _ ⇒ GenericEqualityAssertion(1, 2, negate = true))
      val s = Scenario("scenario with unresolved", step :: Nil)
      ScenarioRunner.runScenario(Session.newEmpty)(s).map(_.isSuccess should be(true))
    }
  }
}
