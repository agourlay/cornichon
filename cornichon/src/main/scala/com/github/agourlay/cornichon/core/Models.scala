package com.github.agourlay.cornichon.core

case class FeatureDef(name: String, scenarios: List[Scenario], ignored: Boolean = false) {
  require(
    scenarios.map(s ⇒ s.name).distinct.length == scenarios.length,
    s"Scenarios name must be unique within a Feature - error caused by duplicated declaration of scenarios '${scenarios.map(s ⇒ s.name).diff(scenarios.map(s ⇒ s.name).distinct).mkString(", ")}'"
  )
}

case class Scenario(name: String, steps: List[Step], ignored: Boolean = false)