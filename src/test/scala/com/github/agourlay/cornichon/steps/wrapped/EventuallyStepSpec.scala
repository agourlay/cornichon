package com.github.agourlay.cornichon.steps.wrapped

import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.steps.StepUtilSpec
import com.github.agourlay.cornichon.steps.regular.assertStep.{ AssertStep, GenericAssertion }
import org.scalatest.{ Matchers, AsyncWordSpec }

import scala.concurrent.duration._

class EventuallyStepSpec extends AsyncWordSpec with Matchers with StepUtilSpec {

  "EventuallyStep" must {
    "replay eventually wrapped steps" in {
      val session = Session.newEmpty
      val eventuallyConf = EventuallyConf(maxTime = 5.seconds, interval = 10.milliseconds)
      val nested: Vector[Step] = Vector(
        AssertStep(
          "possible random value step",
          s ⇒ GenericAssertion(scala.util.Random.nextInt(10), 5)
        )
      )

      val steps = Vector(EventuallyStep(nested, eventuallyConf))
      val s = Scenario("scenario with eventually", steps)
      engine.runScenario(session)(s).map(_.isSuccess should be(true))
    }

    "replay eventually wrapped steps until limit" in {
      val session = Session.newEmpty
      val eventuallyConf = EventuallyConf(maxTime = 10.milliseconds, interval = 1.milliseconds)
      val nested: Vector[Step] = Vector(
        AssertStep(
          "impossible random value step", s ⇒ GenericAssertion(11, scala.util.Random.nextInt(10))
        )
      )
      val steps = Vector(
        EventuallyStep(nested, eventuallyConf)
      )
      val s = Scenario("scenario with eventually that fails", steps)
      engine.runScenario(session)(s).map(_.isSuccess should be(false))
    }

  }

}
