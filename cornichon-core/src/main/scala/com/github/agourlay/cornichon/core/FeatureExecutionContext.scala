package com.github.agourlay.cornichon.core

import FeatureExecutionContext._
import com.github.agourlay.cornichon.resolver.PlaceholderResolver

case class FeatureExecutionContext(
    beforeSteps: List[Step],
    finallySteps: List[Step],
    featureIgnored: Boolean,
    focusedScenarios: Set[String],
    withSeed: Option[Long],
    placeholderResolver: PlaceholderResolver) {

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
  val empty = FeatureExecutionContext(
    beforeSteps = Nil,
    finallySteps = Nil,
    featureIgnored = false,
    focusedScenarios = Set.empty,
    withSeed = Some(1L),
    placeholderResolver = PlaceholderResolver.default())
  private val someFeatureIgnored = Some("feature ignored")
  private val someNoFocus = Some("no focus")
}
