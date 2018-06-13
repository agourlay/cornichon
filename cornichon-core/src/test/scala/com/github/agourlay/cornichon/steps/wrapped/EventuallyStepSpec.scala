package com.github.agourlay.cornichon.steps.wrapped

import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.steps.StepUtilSpec
import com.github.agourlay.cornichon.steps.regular.assertStep.{ AssertStep, Assertion, GenericEqualityAssertion }
import org.scalatest.{ AsyncWordSpec, Matchers }

import scala.concurrent.duration._

class EventuallyStepSpec extends AsyncWordSpec with Matchers with StepUtilSpec {

  "EventuallyStep" must {
    "replay eventually wrapped steps" in {
      val eventuallyConf = EventuallyConf(maxTime = 5.seconds, interval = 10.milliseconds)
      val nested = AssertStep(
        "possible random value step",
        s ⇒ GenericEqualityAssertion(scala.util.Random.nextInt(10), 5)
      ) :: Nil

      val steps = EventuallyStep(nested, eventuallyConf) :: Nil
      val s = Scenario("scenario with eventually", steps)
      engine.runScenario(Session.newEmpty)(s).map(_.isSuccess should be(true))
    }

    "replay eventually wrapped steps until limit" in {
      val eventuallyConf = EventuallyConf(maxTime = 1.seconds, interval = 100.milliseconds)
      var counter = 0
      val nested = AssertStep(
        "impossible random value step", s ⇒ {
          counter = counter + 1
          Assertion.failWith("nop!")
        }
      ) :: Nil
      val eventuallyStep = EventuallyStep(nested, eventuallyConf)
      val s = Scenario("scenario with eventually that fails", eventuallyStep :: Nil)
      engine.runScenario(Session.newEmpty)(s).map { r ⇒
        r.isSuccess should be(false)
        counter <= 10 should be(true) // at most 10*100millis
      }
    }

    "replay eventually handle hanging wrapped steps" in {
      val eventuallyConf = EventuallyConf(maxTime = 1.seconds, interval = 100.milliseconds)
      val nested = AssertStep(
        "slow always true step", s ⇒ {
          Thread.sleep(100000)
          Assertion.alwaysValid
        }
      ) :: Nil
      val eventuallyStep = EventuallyStep(nested, eventuallyConf)
      val s = Scenario("scenario with eventually that fails", eventuallyStep :: Nil)
      engine.runScenario(Session.newEmpty)(s).map {
        case f: FailureScenarioReport ⇒
          f.isSuccess should be(false)
          f.msg should be("""Scenario 'scenario with eventually that fails' failed:
                            |
                            |at step:
                            |Eventually block with maxDuration = 1 second and interval = 100 milliseconds
                            |
                            |with error(s):
                            |Eventually block is interrupted due to a long period of inactivity
                            |""".stripMargin)
        case other @ _ ⇒
          fail(s"should have failed but got $other")
      }
    }

  }

}
