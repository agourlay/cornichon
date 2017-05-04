package com.github.agourlay.cornichon.examples

import com.github.agourlay.cornichon.CornichonFeature
import scala.concurrent.duration._

class DummyExamplesTwo extends CornichonFeature {

  def feature = Feature("Dummy two feature") {

    Scenario("session access") {

      When I save("arg1" → "cornichon")

      Then assert session_value("arg1").is("cornichon")

    }

    Scenario("waiting step") {

      And I wait(1.second)

    }
  }

  beforeEachScenario(
    print_step("before each scenario dummy two")
  )

  afterEachScenario(
    print_step("before each scenario dummy two")
  )

}