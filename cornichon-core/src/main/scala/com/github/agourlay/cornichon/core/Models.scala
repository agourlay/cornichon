package com.github.agourlay.cornichon.core

import scala.collection.breakOut

case class FeatureDef(name: String, scenarios: List[Scenario], ignored: Boolean = false) {
  require(
    scenarios.map(s ⇒ s.name).distinct.length == scenarios.length,
    s"Scenarios name must be unique within a Feature - error caused by duplicated declaration of scenarios '${scenarios.map(s ⇒ s.name).diff(scenarios.map(s ⇒ s.name).distinct).mkString(", ")}'"
  )

  val focusedScenarios: Set[String] = scenarios.collect { case s if s.focused ⇒ s.name }(breakOut)
}

case class Scenario(name: String, steps: List[Step], ignored: Boolean = false, pending: Boolean = false, focused: Boolean = false)