package com.github.agourlay.cornichon.core

import utest._

object FeatureSpec extends TestSuite {

  val tests = Tests {
    test("a Feature have Scenarios with unique names") {
      intercept[IllegalArgumentException] {
        FeatureDef("malformed feature", Scenario("doingstuff", Nil) :: Scenario("doingstuff", Nil) :: Nil)
      }
    }
  }

}
