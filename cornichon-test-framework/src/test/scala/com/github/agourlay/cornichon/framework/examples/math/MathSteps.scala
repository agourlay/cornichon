package com.github.agourlay.cornichon.framework.examples.math

import com.github.agourlay.cornichon.CornichonFeature
import com.github.agourlay.cornichon.core.Step
import com.github.agourlay.cornichon.steps.regular.assertStep._
import com.github.agourlay.cornichon.steps.cats.EffectStep

import scala.util.Random

trait MathSteps {
  this: CornichonFeature ⇒

  case class adding_values(arg1: String, arg2: String) {
    def equals(res: Int) = AssertStep(
      title = s"value of $arg1 + $arg2 should be $res",
      action = sc ⇒ Assertion.either {
        for {
          v1 ← sc.session.get(arg1).map(_.toInt)
          v2 ← sc.session.get(arg2).map(_.toInt)
        } yield GenericEqualityAssertion(res, v1 + v2)
      }
    )
  }

  def generate_random_int(target: String, max: Int = 10): Step =
    EffectStep.fromSyncE(
      title = s"generate random Int into '$target' (max=$max)",
      effect = _.session.addValue(target, Random.nextInt(max).toString)
    )

  def generate_random_double(target: String): Step =
    EffectStep.fromSyncE(
      title = s"generate random Double into '$target'",
      effect = _.session.addValue(target, Random.nextDouble().toString)
    )

  case class double_value(source: String) {
    def isBetween(low: Double, high: Double) =
      AssertStep(
        title = s"double value of '$source' is between '$low' and '$high'",
        action = sc ⇒ Assertion.either {
          sc.session.get(source).map(v ⇒ BetweenAssertion(low, v.toDouble, high))
        }
      )
  }

  def calculate_point_in_circle(target: String): Step = EffectStep.fromSyncE(
    title = s"calculate points inside circle",
    effect = sc ⇒ {
      for {
        x ← sc.session.get("x").map(_.toDouble)
        y ← sc.session.get("y").map(_.toDouble)
        inside = Math.sqrt(x * x + y * y) <= 1
        ns ← sc.session.addValue(target, if (inside) "1" else "0")
      } yield ns
    }
  )

  def estimate_pi_from_ratio(inside: String, target: String): Step =
    EffectStep.fromSyncE(
      title = s"estimate PI from ratio into key '$target'",
      effect = sc ⇒ {
        sc.session.getHistory(inside).flatMap { insides ⇒
          val trial = insides.size
          val estimation = (insides.count(_ == "1").toDouble / trial) * 4
          sc.session.addValue(target, estimation.toString)
        }
      }
    )

  def is_valid_sum: Step = AssertStep(
    title = "sum of 'a' + 'b' = 'c'",
    action = sc ⇒ {
      val s = sc.session
      GenericEqualityAssertion(s.getUnsafe("c").toInt, s.getUnsafe("a").toInt + s.getUnsafe("b").toInt)
    }
  )
}
