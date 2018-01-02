package com.github.agourlay.cornichon.core

case class FeatureExecutionContext(
    beforeSteps: List[Step] = Nil,
    finallySteps: List[Step] = Nil,
    featureIgnored: Boolean = false,
    focusedScenarios: Set[String] = Set.empty) {

  def isIgnored(scenario: Scenario): Boolean =
    featureIgnored || scenario.ignored || (focusedScenarios.nonEmpty && !focusedScenarios.contains(scenario.name))

  def isPending(scenario: Scenario): Boolean =
    scenario.pending
}

object FeatureExecutionContext {
  val empty = FeatureExecutionContext()
}
