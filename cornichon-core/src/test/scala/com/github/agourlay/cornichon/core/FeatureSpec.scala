package com.github.agourlay.cornichon.core

import munit.FunSuite

class FeatureSpec extends FunSuite {

  test("a Feature have Scenarios with unique names") {
    intercept[IllegalArgumentException] {
      val f = FeatureDef("malformed feature", Scenario("doing stuff", Nil) :: Scenario("doing stuff", Nil) :: Nil)
      assert(f.name == "malformed feature") // never reached
    }
  }

  test("a Feature can be created with unique scenario names") {
    val f = FeatureDef("valid feature", Scenario("first", Nil) :: Scenario("second", Nil) :: Nil)
    assertEquals(f.name, "valid feature")
    assertEquals(f.scenarios.size, 2)
  }

  test("a Feature can have zero scenarios") {
    val f = FeatureDef("empty feature", Nil)
    assertEquals(f.scenarios, Nil)
  }

  test("a Feature tracks focused scenarios") {
    val f = FeatureDef("feature", Scenario("normal", Nil) :: Scenario("focused", Nil, focused = true) :: Nil)
    assertEquals(f.focusedScenarios, Set("focused"))
  }

}
