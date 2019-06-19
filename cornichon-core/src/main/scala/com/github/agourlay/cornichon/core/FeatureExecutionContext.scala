package com.github.agourlay.cornichon.core

import FeatureExecutionContext._

case class FeatureExecutionContext(
    beforeSteps: List[Step] = Nil,
    finallySteps: List[Step] = Nil,
    featureIgnored: Boolean = false,
    focusedScenarios: Set[String] = Set.empty,
    withSeed: Option[Long] = None) {

  def isIgnored(scenario: Scenario): Option[String] =
    if (featureIgnored)
      someFeatureIgnored
    else
      scenario.ignored match {
        case Some(_) ⇒
          scenario.ignored
        case None if focusedScenarios.nonEmpty && !focusedScenarios.contains(scenario.name) ⇒
          someNoFocus
        case _ ⇒ None
      }

  def isPending(scenario: Scenario): Boolean =
    scenario.pending
}

object FeatureExecutionContext {
  val empty = FeatureExecutionContext(withSeed = Some(1L))
  private val someFeatureIgnored = Some("feature ignored")
  private val someNoFocus = Some("no focus")
}
