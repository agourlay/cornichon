package com.github.agourlay.cornichon.steps.wrapped

import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.steps.regular.AssertStep
import org.scalatest.{ Matchers, WordSpec }

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.Duration
import scala.concurrent.duration._

class RepeatDuringStepSpec extends WordSpec with Matchers {

  val engine = new Engine(ExecutionContext.global)

  "RepeatDuringStep" must {
    "fail if 'repeatDuring' block contains a failed step" in {
      val nested: Vector[Step] = Vector(
        AssertStep(
          "always fails",
          s ⇒ SimpleStepAssertion(true, false)
        )
      )
      val steps = Vector(
        RepeatDuringStep(nested, 5.millis)
      )
      val s = Scenario("scenario with RepeatDuring", steps)
      engine.runScenario(Session.newSession)(s).stepsRunReport.isSuccess should be(false)
    }

    "repeat steps inside 'repeatDuring' for at least the duration param" in {
      val nested: Vector[Step] = Vector(
        AssertStep(
          "always valid",
          s ⇒ {
            Thread.sleep(1)
            SimpleStepAssertion(true, true)
          }
        )
      )
      val steps = Vector(
        RepeatDuringStep(nested, 50.millis)
      )
      val s = Scenario("scenario with RepeatDuring", steps)
      val now = System.nanoTime
      engine.runScenario(Session.newSession)(s).stepsRunReport.isSuccess should be(true)
      val executionTime = Duration.fromNanos(System.nanoTime - now)
      executionTime.gt(50.millis) should be(true)
      // empiric values for the upper bound here
      executionTime.lt(55.millis) should be(true)
    }

    "repeat steps inside 'repeatDuring' at least once if they take more time than the duration param" in {
      val nested: Vector[Step] = Vector(
        AssertStep(
          "always valid",
          s ⇒ {
            Thread.sleep(500)
            SimpleStepAssertion(true, true)
          }
        )
      )
      val steps = Vector(
        RepeatDuringStep(nested, 50.millis)
      )
      val s = Scenario("scenario with RepeatDuring", steps)
      val now = System.nanoTime
      engine.runScenario(Session.newSession)(s).stepsRunReport.isSuccess should be(true)
      val executionTime = Duration.fromNanos(System.nanoTime - now)
      //println(executionTime)
      executionTime.gt(50.millis) should be(true)
      // empiric values for the upper bound here
      executionTime.lt(550.millis) should be(true)
    }
  }

}
