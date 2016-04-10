package com.github.agourlay.cornichon.steps.regular

import com.github.agourlay.cornichon.core.{ Engine, Scenario, Session }
import org.scalatest.{ Matchers, WordSpec }

import scala.concurrent.ExecutionContext

class DebugStepSpec extends WordSpec with Matchers {

  val engine = new Engine(ExecutionContext.global)

  "DebugStep" must {
    "return error if a Debug step throw an exception" in {
      val session = Session.newSession
      val step = DebugStep(s ⇒ {
        6 / 0
        "Never gonna read this"
      })
      val s = Scenario("scenario with faulty debug step", Vector(step))
      engine.runScenario(session)(s).stepsRunReport.isSuccess should be(false)
    }
  }
}
