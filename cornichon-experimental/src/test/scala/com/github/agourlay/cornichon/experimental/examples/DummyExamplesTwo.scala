package com.github.agourlay.cornichon.experimental.examples

import com.github.agourlay.cornichon.experimental.CornichonFeature
import scala.concurrent.duration._

class DummyExamplesTwo extends CornichonFeature {

  def feature = Feature("Dummy two feature") {

    Scenario("session access") {

      When I save("arg1" → "cornichon")

      Then assert session_value("arg1").is("cornichon")

    }

    Scenario("waiting step").ignoredBecause("Ignored for a good reason") {

      And I wait(1.second)

    }

    Scenario("TODO") pending

  }

  beforeEachScenario(
    print_step("before each scenario dummy two")
  )

  afterEachScenario(
    print_step("after each scenario dummy two")
  )

}