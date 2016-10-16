package com.github.agourlay.cornichon.core

import org.scalatest.{ Matchers, WordSpec }

class FeatureSpec extends WordSpec with Matchers {

  "a Feature" must {
    "have Scenarios with unique names" in {
      intercept[IllegalArgumentException] {
        FeatureDef("malformed feature", Scenario("doingstuff", Nil) :: Scenario("doingstuff", Nil) :: Nil)
      }
    }
  }

}
