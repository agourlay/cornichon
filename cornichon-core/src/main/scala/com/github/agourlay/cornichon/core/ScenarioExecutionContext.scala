package com.github.agourlay.cornichon.core

case class ScenarioExecutionContext(finallySteps: List[Step] = Nil, featureIgnored: Boolean = false, focusedScenarios: Set[String] = Set.empty) {
  def isIgnored(scenario: Scenario) =
    featureIgnored || scenario.ignored || (focusedScenarios.nonEmpty && !focusedScenarios.contains(scenario.name))

  def isPending(scenario: Scenario) =
    scenario.pending
}

object ScenarioExecutionContext {
  val empty = ScenarioExecutionContext()
}
