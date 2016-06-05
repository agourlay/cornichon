package com.github.agourlay.cornichon.steps.wrapped

import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.steps.regular.AssertStep
import org.scalatest.{ Matchers, WordSpec }

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

class EventuallyStepSpec extends WordSpec with Matchers {

  val engine = new Engine(ExecutionContext.global)

  "EventuallyStep" must {
    "replay eventually wrapped steps" in {
      val session = Session.newSession
      val eventuallyConf = EventuallyConf(maxTime = 5.seconds, interval = 10.milliseconds)
      val nested: Vector[Step] = Vector(
        AssertStep(
          "possible random value step",
          s ⇒ SimpleStepAssertion(scala.util.Random.nextInt(10), 5)
        )
      )

      val steps = Vector(EventuallyStep(nested, eventuallyConf))
      val s = Scenario("scenario with eventually", steps)
      engine.runScenario(session)(s).stepsExecutionResult.isSuccess should be(true)
    }

    "replay eventually wrapped steps until limit" in {
      val session = Session.newSession
      val eventuallyConf = EventuallyConf(maxTime = 10.milliseconds, interval = 1.milliseconds)
      val nested: Vector[Step] = Vector(
        AssertStep(
          "impossible random value step", s ⇒ SimpleStepAssertion(11, scala.util.Random.nextInt(10))
        )
      )
      val steps = Vector(
        EventuallyStep(nested, eventuallyConf)
      )
      val s = Scenario("scenario with eventually that fails", steps)
      engine.runScenario(session)(s).stepsExecutionResult.isSuccess should be(false)
    }

  }

}
