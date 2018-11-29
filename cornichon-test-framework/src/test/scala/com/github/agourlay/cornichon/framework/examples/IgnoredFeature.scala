package com.github.agourlay.cornichon.framework.examples

import com.github.agourlay.cornichon.CornichonFeature
import scala.concurrent.duration._

class IgnoredFeature extends CornichonFeature {
  def feature = Feature("feature testing ignore flag").ignoredBecause("it takes too long!") {
    Scenario("Scenario 1") {
      Given I wait(10.hours)
      And I print_step("Scenario 1 is finished")
    }

    Scenario("Scenario 2") {
      And I print_step("Scenario 2 is finished")
    }

    Scenario("Scenario 3") {
      Given I wait(10.hours)
      And I print_step("Scenario 3 is finished")
    }

    Scenario("Scenario 4") {
      And I print_step("Scenario 4 is finished")
    }
  }
}
