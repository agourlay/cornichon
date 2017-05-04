package com.github.agourlay.cornichon.examples

import com.github.agourlay.cornichon.CornichonFeature

class DummyExamplesOne extends DummyFeatureToTestInheritance {

  def feature = Feature("Dummy one feature") {

    Scenario("session access") {

      When I save("arg1" → "cornichon")

      Then assert session_value("arg1").is("cornichon")

    }

    Scenario("printing step") {

      And I print_step("hello world!")

    }
  }
}

trait DummyFeatureToTestInheritance extends CornichonFeature {

  beforeEachScenario(
    print_step("before each scenario dummy one")
  )

  afterEachScenario(
    print_step("before each scenario dummy one")
  )

}