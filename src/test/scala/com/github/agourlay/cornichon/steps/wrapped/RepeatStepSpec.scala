package com.github.agourlay.cornichon.steps.wrapped

import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.steps.regular.AssertStep
import org.scalatest.{ Matchers, WordSpec }

import scala.concurrent.ExecutionContext

class RepeatStepSpec extends WordSpec with Matchers {

  val engine = new Engine(ExecutionContext.global)

  "RepeatStep" must {
    "fail if 'repeat' block contains a failed step" in {
      val nested: Vector[Step] = Vector(
        AssertStep(
          "always fails",
          s ⇒ SimpleStepAssertion(true, false)
        )
      )
      val steps = Vector(
        RepeatStep(nested, 5)
      )
      val s = Scenario("scenario with Repeat", steps)
      engine.runScenario(Session.newSession)(s).stepsRunReport.isSuccess should be(false)
    }

    "repeat steps inside a 'repeat' block" in {
      var uglyCounter = 0
      val loop = 5
      val nested: Vector[Step] = Vector(
        AssertStep(
          "increment captured counter",
          s ⇒ {
            uglyCounter = uglyCounter + 1
            SimpleStepAssertion(true, true)
          }
        )
      )
      val steps = Vector(
        RepeatStep(nested, loop)
      )
      val s = Scenario("scenario with Repeat", steps)
      engine.runScenario(Session.newSession)(s).stepsRunReport.isSuccess should be(true)
      uglyCounter should be(loop)
    }
  }
}
