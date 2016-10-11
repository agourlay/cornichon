package com.github.agourlay.cornichon.steps.wrapped

import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.steps.StepUtilSpec
import com.github.agourlay.cornichon.steps.regular.assertStep.{ AssertStep, GenericAssertion }
import org.scalatest.{ Matchers, AsyncWordSpec }

import scala.concurrent.duration._

class WithinStepSpec extends AsyncWordSpec with Matchers with StepUtilSpec {

  "WithinStep" must {
    "control duration of 'within' wrapped steps" in {
      val session = Session.newEmpty
      val d = 200.millis
      val nested: Vector[Step] = Vector(
        AssertStep(
          "possible random value step",
          s ⇒ {
            Thread.sleep(100)
            GenericAssertion(true, true)
          }
        )
      )
      val steps = Vector(
        WithinStep(nested, d)
      )
      val s = Scenario("scenario with Within", steps)
      engine.runScenario(session)(s).map(_.isSuccess should be(true))
    }

    "fail if duration of 'within' is exceeded" in {
      val session = Session.newEmpty
      val d = 200.millis
      val nested: Vector[Step] = Vector(
        AssertStep(
          "possible random value step",
          s ⇒ {
            Thread.sleep(250)
            GenericAssertion(true, true)
          }
        )
      )
      val steps = Vector(
        WithinStep(nested, d)
      )
      val s = Scenario("scenario with Within", steps)
      engine.runScenario(session)(s).map(_.isSuccess should be(false))
    }
  }

}
