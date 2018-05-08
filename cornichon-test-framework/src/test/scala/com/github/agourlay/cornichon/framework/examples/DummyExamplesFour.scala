package com.github.agourlay.cornichon.framework.examples

import com.github.agourlay.cornichon.framework.CornichonFeature

class DummyExamplesFour extends CornichonFeature {

  def feature = Feature("Dummy four feature") {

    Scenario("single scenario pending").pending

  }
}
