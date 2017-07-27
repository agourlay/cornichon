package com.github.agourlay.cornichon.examples.math

import com.github.agourlay.cornichon.CornichonFeature
import com.github.agourlay.cornichon.steps.regular.assertStep._
import com.github.agourlay.cornichon.steps.regular.EffectStep

import scala.util.Random

trait MathSteps {
  this: CornichonFeature ⇒

  case class adding_values(arg1: String, arg2: String) {
    def equals(res: Int) = AssertStep(
      title = s"value of $arg1 + $arg2 should be $res",
      action = s ⇒ Assertion.either {
      for {
        v1 ← s.get(arg1).map(_.toInt)
        v2 ← s.get(arg2).map(_.toInt)
      } yield GenericEqualityAssertion(res, v1 + v2)
    }
    )
  }

  def generate_random_int(target: String, max: Int = 10) =
    EffectStep.fromSync(
      title = s"generate random Int into '$target' (max=$max)",
      effect = s ⇒ s.addValue(target, Random.nextInt(max).toString)
    )

  def generate_random_double(target: String) =
    EffectStep.fromSync(
      title = s"generate random Double into '$target'",
      effect = s ⇒ s.addValue(target, Random.nextDouble().toString)
    )

  case class double_value(source: String) {
    def isBetween(low: Double, high: Double) =
      AssertStep(
        title = s"double value of '$source' is between '$low' and '$high'",
        action = s ⇒ Assertion.either {
        s.get(source).map(v ⇒ BetweenAssertion(low, v.toDouble, high))
      }
      )
  }

  def calculate_point_in_circle(target: String) = EffectStep.fromSyncE(
    title = s"calculate points inside circle",
    effect = s ⇒ {
    for {
      x ← s.get("x").map(_.toDouble)
      y ← s.get("y").map(_.toDouble)
      inside = Math.sqrt(x * x + y * y) <= 1
    } yield s.addValue(target, if (inside) "1" else "0")
  }
  )

  def estimate_pi_from_ratio(inside: String, target: String) =
    EffectStep.fromSync(
      title = s"estimate PI from ratio into key '$target'",
      effect = s ⇒ {
      val insides = s.getHistory(inside)
      val trial = insides.size
      val estimation = (insides.count(_ == "1").toDouble / trial) * 4
      s.addValue(target, estimation.toString)
    }
    )
}
