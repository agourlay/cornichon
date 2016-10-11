package com.github.agourlay.cornichon.steps.wrapped

import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.steps.StepUtilSpec
import com.github.agourlay.cornichon.steps.regular.assertStep.{ AssertStep, GenericAssertion }
import org.scalatest.{ Matchers, AsyncWordSpec }

import scala.concurrent.duration._

class RepeatDuringStepSpec extends AsyncWordSpec with Matchers with StepUtilSpec {

  "RepeatDuringStep" must {
    "fail if 'repeatDuring' block contains a failed step" in {
      val nested: Vector[Step] = Vector(
        AssertStep(
          "always fails",
          s ⇒ GenericAssertion(true, false)
        )
      )
      val steps = Vector(
        RepeatDuringStep(nested, 5.millis)
      )
      val s = Scenario("scenario with RepeatDuring", steps)
      engine.runScenario(Session.newEmpty)(s).map(_.isSuccess should be(false))
    }

    "repeat steps inside 'repeatDuring' for at least the duration param" in {
      val nested: Vector[Step] = Vector(
        AssertStep(
          "always valid",
          s ⇒ {
            Thread.sleep(1)
            GenericAssertion(true, true)
          }
        )
      )
      val steps = Vector(
        RepeatDuringStep(nested, 50.millis)
      )
      val s = Scenario("scenario with RepeatDuring", steps)
      val now = System.nanoTime
      engine.runScenario(Session.newEmpty)(s).map { res ⇒
        res.isSuccess should be(true)
        val executionTime = Duration.fromNanos(System.nanoTime - now)
        withClue(executionTime.toMillis) {
          executionTime.gt(50.millis) should be(true)
          // empiric values for the upper bound here
          executionTime.lt(60.millis) should be(true)
        }
      }
    }

    "repeat steps inside 'repeatDuring' at least once if they take more time than the duration param" in {
      val nested: Vector[Step] = Vector(
        AssertStep(
          "always valid",
          s ⇒ {
            Thread.sleep(500)
            GenericAssertion(true, true)
          }
        )
      )
      val steps = Vector(
        RepeatDuringStep(nested, 50.millis)
      )
      val s = Scenario("scenario with RepeatDuring", steps)
      val now = System.nanoTime
      engine.runScenario(Session.newEmpty)(s).map { res ⇒
        res.isSuccess should be(true)
        val executionTime = Duration.fromNanos(System.nanoTime - now)
        withClue(executionTime.toMillis) {
          executionTime.gt(50.millis) should be(true)
          // empiric values for the upper bound here
          executionTime.lt(550.millis) should be(true)
        }
      }
    }
  }

}
