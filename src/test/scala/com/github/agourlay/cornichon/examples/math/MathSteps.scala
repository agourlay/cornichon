package com.github.agourlay.cornichon.examples.math

import com.github.agourlay.cornichon.steps.regular.{ AssertStep, DetailedAssertion, EffectStep, SimpleAssertion }

import scala.util.Random

trait MathSteps {

  case class adding_values(arg1: String, arg2: String) {
    def equals(res: Int) = AssertStep(
      title = s"value of $arg1 + $arg2 should be $res",
      action = s ⇒ {
      val v1 = s.get(arg1).toInt
      val v2 = s.get(arg2).toInt
      SimpleAssertion(res, v1 + v2)
    }
    )
  }

  def generate_random_int(target: String, max: Int = 100) =
    EffectStep(
      title = s"generate random Int into '$target' (max=$max)",
      effect = s ⇒ {
      s.addValue(target, Random.nextInt(max).toString)
    }
    )

  def generate_random_double(target: String) =
    EffectStep(
      title = s"generate random Double into '$target'",
      effect = s ⇒ {
      s.addValue(target, Random.nextDouble().toString)
    }
    )

  case class double_value(source: String) {
    def isBetween(low: Double, high: Double) =
      AssertStep(
        title = s"double value of '$source' is between '$low' and '$high'",
        action = s ⇒ {
        val v = s.get(source).toDouble
        DetailedAssertion(true, v > low && v < high, ratioError(v, low, high))
      }
      )
  }

  private def ratioError(v: Double, low: Double, high: Double): Boolean ⇒ String = b ⇒ s"$v is not between $low and $high"

  def calculate_point_in_circle(target: String) = EffectStep(
    title = s"calculate points inside circle",
    effect = s ⇒ {
    val x = s.get("x").toDouble
    val y = s.get("y").toDouble
    val inside = Math.sqrt(x * x + y * y) <= 1
    s.addValue(target, if (inside) "1" else "0")
  }
  )

  def estimate_pi_from_ratio(inside: String, target: String) =
    EffectStep(
      title = s"estimate PI from ratio into key '$target'",
      effect = s ⇒ {
      val insides = s.getHistory(inside)
      val trial = insides.size
      val estimation = (insides.count(_ == "1").toDouble / trial) * 4
      s.addValue(target, estimation.toString)
    }
    )
}
