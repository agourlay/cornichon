package com.github.agourlay.cornichon.framework.examples

import com.github.agourlay.cornichon.CornichonFeature

import scala.concurrent.duration._

class DummyExamplesThree extends CornichonFeature {

  def feature = Feature("Dummy three feature") {

    Scenario("session access") {

      When I save("arg1" → "cornichon")

      Then assert session_value("arg1").is("cornichon")

    }

    Scenario("waiting step") {

      And I wait(1.second)

    }
  }

  beforeEachScenario(
    print_step("before each scenario dummy three")
  )

  afterEachScenario(
    print_step("after each scenario dummy three")
  )

}