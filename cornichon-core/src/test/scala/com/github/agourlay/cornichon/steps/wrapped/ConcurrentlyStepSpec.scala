package com.github.agourlay.cornichon.steps.wrapped

import java.util.concurrent.atomic.AtomicInteger

import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.steps.StepUtilSpec
import com.github.agourlay.cornichon.steps.regular.assertStep.{ AssertStep, GenericEqualityAssertion }
import org.scalatest.{ Matchers, AsyncWordSpec }

import scala.concurrent.duration._

class ConcurrentlyStepSpec extends AsyncWordSpec with Matchers with StepUtilSpec {

  "ConcurrentlyStep" must {
    "fail if 'concurrently' block contains a failed step" in {
      val nested = AssertStep(
        "always fails",
        s ⇒ GenericEqualityAssertion(true, false)
      ) :: Nil
      val steps = ConcurrentlyStep(nested, 3, 200.millis) :: Nil
      val s = Scenario("scenario with Concurrently", steps)
      engine.runScenario(Session.newEmpty)(s).map {
        case f: FailureScenarioReport ⇒
          f.failedSteps.head.errors.head.renderedMessage should be("expected result was:\n'true'\nbut actual result is:\n'false'")
        case _ ⇒ assert(false)
      }
    }

    "fail if 'concurrently' block does not complete within 'maxDuraiton because of a single step duration" in {
      val nested = AssertStep(
        "always succeed after 1000 ms",
        s ⇒ {
          Thread.sleep(1000)
          GenericEqualityAssertion(true, true)
        }
      ) :: Nil
      val steps = ConcurrentlyStep(nested, 1, 200.millis) :: Nil
      val s = Scenario("scenario with Concurrently", steps)
      engine.runScenario(Session.newEmpty)(s).map {
        case f: FailureScenarioReport ⇒ f.failedSteps.head.errors.head.renderedMessage should be("Concurrently block did not reach completion in time: 0/1 finished")
        case _                        ⇒ assert(false)
      }
    }

    "run nested block 'n' times" in {
      val uglyCounter = new AtomicInteger(0)
      val loop = 5
      val nested = AssertStep(
        "increment captured counter",
        s ⇒ {
          uglyCounter.incrementAndGet()
          GenericEqualityAssertion(true, true)
        }
      ) :: Nil
      val concurrentlyStep = ConcurrentlyStep(nested, loop, 300.millis)
      val s = Scenario("scenario with Concurrently", concurrentlyStep :: Nil)
      engine.runScenario(Session.newEmpty)(s).map { res ⇒
        res.isSuccess should be(true)
        uglyCounter.intValue() should be(loop)
      }
    }
  }

}
