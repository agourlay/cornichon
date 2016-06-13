package com.github.agourlay.cornichon.steps.regular

import com.github.agourlay.cornichon.core.{ Engine, Scenario, Session, SimpleStepAssertion }
import org.scalatest.{ Matchers, WordSpec }

import scala.concurrent.ExecutionContext

class AssertStepSpec extends WordSpec with Matchers {

  val engine = new Engine(ExecutionContext.global)

  "AssertStep" must {

    "fail if instruction throws exception" in {
      val session = Session.newSession
      val steps = Vector(
        AssertStep[Int]("stupid step", s ⇒ {
          6 / 0
          SimpleStepAssertion(2, 2)
        })
      )
      val s = Scenario("scenario with stupid test", steps)
      engine.runScenario(session)(s).isSuccess should be(false)
    }

    "success if non equality was expected" in {
      val session = Session.newSession
      val steps = Vector(
        AssertStep(
          "non equals step", s ⇒ SimpleStepAssertion(1, 2), negate = true
        )
      )
      val s = Scenario("scenario with unresolved", steps)
      engine.runScenario(session)(s).isSuccess should be(true)
    }
  }

  "runStepPredicate" must {
    "return session if assertion is True" in {
      val session = Session.newSession
      val assertion = SimpleStepAssertion(2, 2)
      val step = AssertStep[Int]("stupid step", s ⇒ assertion)
      step.runStepPredicate(session, assertion).fold(e ⇒ fail("should have been Right"), s ⇒ s should be(session))
    }
  }
}
