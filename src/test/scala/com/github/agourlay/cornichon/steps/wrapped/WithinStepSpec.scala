package com.github.agourlay.cornichon.steps.wrapped

import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.steps.regular.AssertStep
import org.scalatest.{ Matchers, WordSpec }

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

class WithinStepSpec extends WordSpec with Matchers {

  val engine = new Engine(ExecutionContext.global)

  "WithinStep" must {
    "control duration of 'within' wrapped steps" in {
      val session = Session.newSession
      val d = 200.millis
      val nested: Vector[Step] = Vector(
        AssertStep(
          "possible random value step",
          s ⇒ {
            Thread.sleep(100)
            SimpleStepAssertion(true, true)
          }
        )
      )
      val steps = Vector(
        WithinStep(nested, d)
      )
      val s = Scenario("scenario with Within", steps)
      engine.runScenario(session)(s).stepsExecutionResult.isSuccess should be(true)
    }

    "fail if duration of 'within' is exceeded" in {
      val session = Session.newSession
      val d = 200.millis
      val nested: Vector[Step] = Vector(
        AssertStep(
          "possible random value step",
          s ⇒ {
            Thread.sleep(250)
            SimpleStepAssertion(true, true)
          }
        )
      )
      val steps = Vector(
        WithinStep(nested, d)
      )
      val s = Scenario("scenario with Within", steps)
      engine.runScenario(session)(s).stepsExecutionResult.isSuccess should be(false)
    }
  }

}
