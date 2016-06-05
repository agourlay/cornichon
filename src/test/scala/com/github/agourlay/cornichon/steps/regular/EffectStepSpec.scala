package com.github.agourlay.cornichon.steps.regular

import com.github.agourlay.cornichon.core.{ Engine, Scenario, Session }
import org.scalatest.{ Matchers, WordSpec }

import scala.concurrent.ExecutionContext

class EffectStepSpec extends WordSpec with Matchers {

  val engine = new Engine(ExecutionContext.global)

  "EffectStep" must {
    "return error if an Effect step throw an exception" in {
      val session = Session.newSession
      val step = EffectStep(title = "buggy effect", s ⇒ {
        6 / 0
        s
      })
      val s = Scenario("scenario with broken effect step", Vector(step))
      engine.runScenario(session)(s).isSuccess should be(false)
    }
  }
}

