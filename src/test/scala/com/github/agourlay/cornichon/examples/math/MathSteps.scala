package com.github.agourlay.cornichon.examples.math

import com.github.agourlay.cornichon.core.SimpleStepAssertion
import com.github.agourlay.cornichon.steps.regular.{ AssertStep, EffectStep }

import scala.util.Random

trait MathSteps {

  case class adding_values(arg1: String, arg2: String) {
    def equals(res: Int) = AssertStep(
      title = s"Value of $arg1 + $arg2 should be $res",
      action = s ⇒ {
      val v1 = s.get(arg1).toInt
      val v2 = s.get(arg2).toInt
      SimpleStepAssertion(res, v1 + v2)
    }
    )
  }

  def generate_random_value(target: String, max: Int = 100) =
    EffectStep(
      title = s"Generate random number into $target (max=$max)",
      effect = s ⇒ {
      s.addValue(target, Random.nextInt(max).toString)
    }
    )
}
