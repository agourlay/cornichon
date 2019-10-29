package com.github.agourlay.cornichon.steps.wrapped

import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.steps.StepUtilSpec
import com.github.agourlay.cornichon.steps.regular.assertStep.{ AssertStep, GenericEqualityAssertion }
import org.scalatest.{ Matchers, AsyncWordSpec }

import scala.concurrent.duration._

class WithinStepSpec extends AsyncWordSpec with Matchers with StepUtilSpec {

  "WithinStep" must {
    "control duration of 'within' wrapped steps" in {
      val d = 100.millis
      val nested = AssertStep(
        "possible random value step",
        _ => {
          Thread.sleep(50)
          GenericEqualityAssertion(true, true)
        }
      ) :: Nil
      val withinStep = WithinStep(nested, d)
      val s = Scenario("scenario with Within", withinStep :: Nil)
      ScenarioRunner.runScenario(Session.newEmpty)(s).map(_.isSuccess should be(true))
    }

    "fail if duration of 'within' is exceeded" in {
      val d = 100.millis
      val nested = AssertStep(
        "possible random value step",
        _ => {
          Thread.sleep(150)
          GenericEqualityAssertion(true, true)
        }
      ) :: Nil
      val withinStep = WithinStep(nested, d)
      val s = Scenario("scenario with Within", withinStep :: Nil)
      ScenarioRunner.runScenario(Session.newEmpty)(s).map(_.isSuccess should be(false))
    }
  }

}
