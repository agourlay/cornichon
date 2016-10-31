package com.github.agourlay.cornichon.steps.regular

import com.github.agourlay.cornichon.core.{ Scenario, Session }
import com.github.agourlay.cornichon.steps.StepUtilSpec
import com.github.agourlay.cornichon.steps.regular.assertStep.{ AssertStep, GenericAssertion }
import org.scalatest.{ AsyncWordSpec, Matchers }

class AssertStepSpec extends AsyncWordSpec with Matchers with StepUtilSpec {

  "AssertStep" must {

    "fail if instruction throws exception" in {
      val session = Session.newEmpty
      val step = AssertStep[Int]("stupid step", s ⇒ {
        6 / 0
        GenericAssertion(2, 2)
      })
      val s = Scenario("scenario with stupid test", step :: Nil)
      engine.runScenario(session)(s).map(_.isSuccess should be(false))
    }

    "success if non equality was expected" in {
      val session = Session.newEmpty
      val step = AssertStep("non equals step", s ⇒ GenericAssertion(1, 2, negate = true))
      val s = Scenario("scenario with unresolved", step :: Nil)
      engine.runScenario(session)(s).map(_.isSuccess should be(true))
    }
  }

  "runStepPredicate" must {
    "return session if assertion is True" in {
      val session = Session.newEmpty
      val assertion = GenericAssertion(2, 2)
      val step = AssertStep[Int]("stupid step", s ⇒ assertion)
      step.runStepPredicate(session)(assertion).fold(e ⇒ fail("should have been Right"), s ⇒ s should be(session))
    }
  }
}
