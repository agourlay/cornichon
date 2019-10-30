package com.github.agourlay.cornichon.steps.wrapped

import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.steps.regular.assertStep.{ AssertStep, GenericEqualityAssertion }
import com.github.agourlay.cornichon.testHelpers.CommonSpec
import utest._

import scala.concurrent.duration._

object WithinStepSpec extends TestSuite with CommonSpec {

  val tests = Tests {
    test("controls duration of 'within' wrapped steps") {
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
      val res = awaitTask(ScenarioRunner.runScenario(Session.newEmpty)(s))
      assert(res.isSuccess)
    }

    test("fails if duration of 'within' is exceeded") {
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
      val res = awaitTask(ScenarioRunner.runScenario(Session.newEmpty)(s))
      assert(!res.isSuccess)
    }
  }
}
