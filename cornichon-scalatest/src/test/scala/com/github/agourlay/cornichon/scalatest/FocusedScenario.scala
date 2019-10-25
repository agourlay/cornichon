package com.github.agourlay.cornichon.scalatest

import com.github.agourlay.cornichon.CornichonFeature

import scala.concurrent.duration._

class FocusedScenario extends CornichonFeature {
  def feature = Feature("feature with scenario focus") {
    Scenario("Scenario 1") {
      Given I wait(10.hours)
    }

    Scenario("Scenario 2").focused {
      Given I save("test-key" -> "test-value")
      Then assert session_value("test-key").is("test-value")
    }

    Scenario("Scenario 3") {
      Given I wait(10.hours)
    }

    Scenario("Scenario 4").focused {
      Given I save("test-key" -> "test-value")
      Then assert session_value("test-key").is("test-value")
    }
  }
}
