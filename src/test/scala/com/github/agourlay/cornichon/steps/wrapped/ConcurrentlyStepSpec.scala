package com.github.agourlay.cornichon.steps.wrapped

import java.util.concurrent.atomic.AtomicInteger

import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.steps.StepUtilSpec
import com.github.agourlay.cornichon.steps.regular.{ AssertStep, GenericAssertion }
import org.scalatest.{ Matchers, WordSpec }

import scala.concurrent.duration._

class ConcurrentlyStepSpec extends WordSpec with Matchers with StepUtilSpec {

  "ConcurrentlyStep" must {
    "fail if 'concurrently' block contains a failed step" in {
      val nested: Vector[Step] = Vector(
        AssertStep(
          "always fails",
          s ⇒ GenericAssertion(true, false)
        )
      )
      val steps = Vector(
        ConcurrentlyStep(nested, 3, 200.millis)
      )
      val s = Scenario("scenario with Concurrently", steps)
      engine.runScenario(Session.newSession)(s).isSuccess should be(false)
    }

    "run nested block 'n' times" in {
      val uglyCounter = new AtomicInteger(0)
      val loop = 5
      val nested: Vector[Step] = Vector(
        AssertStep(
          "increment captured counter",
          s ⇒ {
            uglyCounter.incrementAndGet()
            GenericAssertion(true, true)
          }
        )
      )
      val steps = Vector(
        ConcurrentlyStep(nested, loop, 300.millis)
      )
      val s = Scenario("scenario with Concurrently", steps)
      engine.runScenario(Session.newSession)(s).isSuccess should be(true)
      uglyCounter.intValue() should be(loop)
    }
  }

}
