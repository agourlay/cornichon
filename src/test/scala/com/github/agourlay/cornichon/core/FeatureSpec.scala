package com.github.agourlay.cornichon.core

import org.scalatest.{ Matchers, WordSpec }

class FeatureSpec extends WordSpec with Matchers {

  "a Feature" must {
    "have Scenarios with unique names" in {
      intercept[IllegalArgumentException] {
        FeatureDef("malformed feature", Vector(Scenario("doingstuff", Vector.empty), Scenario("doingstuff", Vector.empty)))
      }
    }
  }

}
