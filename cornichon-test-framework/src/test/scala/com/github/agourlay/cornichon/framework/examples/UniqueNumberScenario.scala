package com.github.agourlay.cornichon.framework.examples

import com.github.agourlay.cornichon.CornichonFeature

class UniqueNumberScenario extends CornichonFeature {

  // running sequentially to get deterministic global assertions
  override lazy val executeScenariosInParallel: Boolean = false

  def feature = Feature("Example of unique number generation") {

    Scenario("unique number scoped to Scenario") {
      When I save("my-counter" -> "<scenario-unique-number>")
      And I save("my-other-counter" -> "<scenario-unique-number>")
      Then assert session_value("my-counter").is("1")
      Then assert session_value("my-other-counter").is("2")
    }

    Scenario("unique number scoped globally (1)") {
      When I save("my-counter" -> "<global-unique-number>")
      Then assert session_value("my-counter").is("1")
    }

    Scenario("unique number scoped globally (2)") {
      When I save("my-counter" -> "<global-unique-number>")
      Then assert session_value("my-counter").is("2")
    }

    Scenario("unique number scoped globally (3)") {
      When I save("my-counter" -> "<global-unique-number>")
      Then assert session_value("my-counter").is("3")
    }

  }
}
