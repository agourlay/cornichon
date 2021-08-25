package com.github.agourlay.cornichon.core

import munit.FunSuite

class FeatureSpec extends FunSuite {
  test("a Feature have Scenarios with unique names") {
    intercept[IllegalArgumentException] {
      val f = FeatureDef("malformed feature", Scenario("doing stuff", Nil) :: Scenario("doing stuff", Nil) :: Nil)
      assert(f.name == "malformed feature") //never reached
    }
  }
}
