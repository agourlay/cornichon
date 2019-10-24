package com.github.agourlay.cornichon.core

import utest._

object FeatureSpec extends TestSuite {

  val tests = Tests {
    test("a Feature have Scenarios with unique names") {
      intercept[IllegalArgumentException] {
        val f = FeatureDef("malformed feature", Scenario("doingstuff", Nil) :: Scenario("doingstuff", Nil) :: Nil)
        assert(f.name == "malformed feature") //never reached
      }
    }
  }

}
