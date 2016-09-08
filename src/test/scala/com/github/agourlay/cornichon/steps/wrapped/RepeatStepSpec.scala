package com.github.agourlay.cornichon.steps.wrapped

import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.steps.StepUtilSpec
import com.github.agourlay.cornichon.steps.regular.assertStep.{ AssertStep, GenericAssertion }
import org.scalatest.{ Matchers, WordSpec }

class RepeatStepSpec extends WordSpec with Matchers with StepUtilSpec {

  "RepeatStep" must {
    "fail if 'repeat' block contains a failed step" in {
      val nested: Vector[Step] = Vector(
        AssertStep(
          "always fails",
          s ⇒ GenericAssertion(true, false)
        )
      )
      val steps = Vector(
        RepeatStep(nested, 5)
      )
      val s = Scenario("scenario with Repeat", steps)
      engine.runScenario(Session.newEmpty)(s).isSuccess should be(false)
    }

    "repeat steps inside a 'repeat' block" in {
      var uglyCounter = 0
      val loop = 5
      val nested: Vector[Step] = Vector(
        AssertStep(
          "increment captured counter",
          s ⇒ {
            uglyCounter = uglyCounter + 1
            GenericAssertion(true, true)
          }
        )
      )
      val steps = Vector(
        RepeatStep(nested, loop)
      )
      val s = Scenario("scenario with Repeat", steps)
      engine.runScenario(Session.newEmpty)(s).isSuccess should be(true)
      uglyCounter should be(loop)
    }
  }
}
