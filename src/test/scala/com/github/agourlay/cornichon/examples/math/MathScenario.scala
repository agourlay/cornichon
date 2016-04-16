package com.github.agourlay.cornichon.examples.math

import com.github.agourlay.cornichon.CornichonFeature

import scala.concurrent.duration._

class MathScenario extends CornichonFeature with MathSteps {

  lazy val feature = Feature("Cornichon feature math examples") {

    Scenario("Simple addition") {

      When I save("arg1" → "2")

      And I save("arg2" → "3")

      Then assert adding_values("arg1", "arg2").equals(5)

    }

    Scenario("Random draw should eventually converge") {

      When I generate_random_value("random-1")

      Eventually(maxDuration = 3 seconds, interval = 1 millis) {

        When I generate_random_value("random-2")

        Then assert session_values("random-1", "random-2").areEquals

      }
    }
  }
}
