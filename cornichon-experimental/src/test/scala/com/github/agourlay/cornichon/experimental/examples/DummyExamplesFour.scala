package com.github.agourlay.cornichon.experimental.examples

import com.github.agourlay.cornichon.experimental.CornichonFeature

class DummyExamplesFour extends CornichonFeature {

  def feature = Feature("Dummy four feature") {

    Scenario("single scenario pending").pending

  }
}
