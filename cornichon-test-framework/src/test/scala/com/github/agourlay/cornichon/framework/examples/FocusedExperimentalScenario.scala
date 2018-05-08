package com.github.agourlay.cornichon.framework.examples

import com.github.agourlay.cornichon.CornichonFeature

import scala.concurrent.duration._

class FocusedExperimentalScenario extends CornichonFeature {
  def feature = Feature("feature/scenario focus") {
    Scenario("Scenario 1") {
      Given I wait(10 hours)
      And I print_step("Scenario 1 is finished")
    }

    Scenario("Scenario 2").focused {
      And I print_step("Scenario 2 is finished")
    }

    Scenario("Scenario 3") {
      Given I wait(10 hours)
      And I print_step("Scenario 3 is finished")
    }

    Scenario("Scenario 4").focused {
      And I print_step("Scenario 4 is finished")
    }
  }
}
