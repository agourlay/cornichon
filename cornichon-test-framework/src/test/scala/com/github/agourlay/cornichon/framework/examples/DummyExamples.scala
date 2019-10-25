package com.github.agourlay.cornichon.framework.examples

import com.github.agourlay.cornichon.CornichonFeature

class DummyExamples extends DummyFeatureToTestInheritance {

  def feature = Feature("Dummy one feature") {

    Scenario("session access") {

      When I save("arg1" → "cornichon")

      Then assert session_value("arg1").is("cornichon")

    }

    Scenario("pending scenario").pending

  }
}

trait DummyFeatureToTestInheritance extends CornichonFeature {

  beforeEachScenario(
    session_value("arg1").isAbsent
  )

  afterEachScenario(
    session_value("arg1").isPresent
  )

  beforeFeature(
    println("before feature")
  )

  afterFeature(
    println("after feature")
  )

}