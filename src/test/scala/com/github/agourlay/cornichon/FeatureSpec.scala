package com.github.agourlay.cornichon

import com.github.agourlay.cornichon.core.Feature.FeatureDef
import com.github.agourlay.cornichon.core.{ Feature, Scenario, Step }
import org.scalatest.{ Matchers, WordSpec }

class FeatureSpec extends WordSpec with Matchers {

  "A feature" must {
    "execute all scenarios" in {
      val steps1 = Seq(Step[Int]("first step", s ⇒ (2 + 2, s), 4))
      val scenario1 = Scenario("test", steps1)

      val steps2 = Seq(
        Step[Int]("first step", s ⇒ (2 + 2, s), 4),
        Step[Int]("second step", s ⇒ (5 * 2, s), 10),
        Step[Int]("third step", s ⇒ (1 - 1, s), 0)
      )
      val scenario2 = Scenario("test", steps2)

      val feature = new Feature {
        val feat = FeatureDef("Playing with Numbers", Seq(scenario1, scenario2))
      }

      feature.runFeature().success should be(true)
    }

    "report failed scenario" in {
      val steps1 = Seq(Step[Int]("first step", s ⇒ (2 + 2, s), 3))
      val scenario1 = Scenario("test", steps1)

      val steps2 = Seq(
        Step[Int]("first step", s ⇒ (2 + 2, s), 4),
        Step[Int]("second step", s ⇒ (5 * 2, s), 10),
        Step[Int]("third step", s ⇒ (1 - 1, s), 0)
      )
      val scenario2 = Scenario("test", steps2)

      val feature = new Feature {
        val feat = FeatureDef("Playing with Numbers", Seq(scenario1, scenario2))
      }

      val report = feature.runFeature()
      report.success should be(false)
    }
  }
}