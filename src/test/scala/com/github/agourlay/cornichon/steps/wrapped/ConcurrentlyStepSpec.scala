package com.github.agourlay.cornichon.steps.wrapped

import java.util.concurrent.atomic.AtomicInteger

import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.steps.StepUtilSpec
import com.github.agourlay.cornichon.steps.regular.assertStep.{ AssertStep, GenericAssertion }
import org.scalatest.{ Matchers, AsyncWordSpec }

import scala.concurrent.duration._

class ConcurrentlyStepSpec extends AsyncWordSpec with Matchers with StepUtilSpec {

  "ConcurrentlyStep" must {
    "fail if 'concurrently' block contains a failed step" in {
      val nested = AssertStep(
        "always fails",
        s ⇒ GenericAssertion(true, false)
      ) :: Nil
      val steps = ConcurrentlyStep(nested, 3, 200.millis) :: Nil
      val s = Scenario("scenario with Concurrently", steps)
      engine.runScenario(Session.newEmpty)(s).map(_.isSuccess should be(false))
    }

    "run nested block 'n' times" in {
      val uglyCounter = new AtomicInteger(0)
      val loop = 5
      val nested = AssertStep(
        "increment captured counter",
        s ⇒ {
          uglyCounter.incrementAndGet()
          GenericAssertion(true, true)
        }
      ) :: Nil
      val concurrentlyStep = ConcurrentlyStep(nested, loop, 300.millis)
      val s = Scenario("scenario with Concurrently", concurrentlyStep :: Nil)
      engine.runScenario(Session.newEmpty)(s).map(_.isSuccess should be(true))
      uglyCounter.intValue() should be(loop)
    }
  }

}
