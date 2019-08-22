package com.github.agourlay.cornichon.framework.examples

import com.github.agourlay.cornichon.CornichonFeature

import scala.concurrent.duration._

class Options extends CornichonFeature {

  def feature = Feature("feature/scenario options") {

    Scenario("ignore scenario").ignoredBecause("takes too long!") {

      Given I wait(10.hours)

    }

    Scenario("remember to add later").pending

  }
}
