package com.github.agourlay.cornichon.core

case class FeatureDef(name: String, scenarios: List[Scenario], ignored: Option[String] = None) {
  require(
    scenarios.map(s => s.name).distinct.length == scenarios.length,
    s"Scenarios name must be unique within a Feature - error caused by duplicated declaration of scenarios '${scenarios.map(s => s.name).diff(scenarios.map(s => s.name).distinct).mkString(", ")}'"
  )

  val focusedScenarios: Set[String] = scenarios.collect { case s if s.focused => s.name }.toSet
}

case class Scenario(
    name: String,
    steps: List[Step],
    ignored: Option[String] = None,
    pending: Boolean = false,
    focused: Boolean = false)