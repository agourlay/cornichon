package com.github.agourlay.cornichon.steps.wrapped

import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.steps.regular.{ AssertStep, SimpleAssertion }
import org.scalatest.{ Matchers, WordSpec }

import scala.concurrent.ExecutionContext

class RetryMaxStepSpec extends WordSpec with Matchers {

  val engine = new Engine(ExecutionContext.global)

  "RetryMaxStep" must {
    "fail if 'retryMax' block never succeeds" in {
      var uglyCounter = 0
      val loop = 10
      val nested: Vector[Step] = Vector(
        AssertStep(
          "always fails",
          s ⇒ {
            uglyCounter = uglyCounter + 1
            SimpleAssertion(true, false)
          }
        )
      )
      val steps = Vector(
        RetryMaxStep(nested, loop)
      )
      val s = Scenario("scenario with RetryMax", steps)
      engine.runScenario(Session.newSession)(s).isSuccess should be(false)
      // Initial run + 'loop' retries
      uglyCounter should be(loop + 1)
    }

    "repeat 'retryMax' and might succeed later" in {
      var uglyCounter = 0
      val max = 10
      val nested: Vector[Step] = Vector(
        AssertStep(
          "always fails",
          s ⇒ {
            uglyCounter = uglyCounter + 1
            SimpleAssertion(true, uglyCounter == max - 2)
          }
        )
      )
      val steps = Vector(
        RetryMaxStep(nested, max)
      )
      val s = Scenario("scenario with RetryMax", steps)
      engine.runScenario(Session.newSession)(s).isSuccess should be(true)
      uglyCounter should be(max - 2)
    }
  }
}
