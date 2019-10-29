package com.github.agourlay.cornichon.core

import FeatureContext._
import com.github.agourlay.cornichon.matchers.{ Matcher, MatcherResolver }
import com.github.agourlay.cornichon.resolver.Mapper

case class FeatureContext(
    beforeSteps: List[Step],
    finallySteps: List[Step],
    featureIgnored: Boolean,
    focusedScenarios: Set[String],
    withSeed: Option[Long],
    customExtractors: Map[String, Mapper],
    allMatchers: Map[String, List[Matcher]]) {

  def isIgnored(scenario: Scenario): Option[String] =
    if (featureIgnored)
      someFeatureIgnored
    else
      scenario.ignored match {
        case Some(_) =>
          scenario.ignored
        case None if focusedScenarios.nonEmpty && !focusedScenarios.contains(scenario.name) =>
          someNoFocus
        case _ => None
      }

  def isPending(scenario: Scenario): Boolean =
    scenario.pending
}

object FeatureContext {
  val empty = FeatureContext(
    beforeSteps = Nil,
    finallySteps = Nil,
    featureIgnored = false,
    focusedScenarios = Set.empty,
    withSeed = Some(1L),
    customExtractors = Map.empty,
    allMatchers = MatcherResolver.builtInMatchers.groupBy(_.key))
  private val someFeatureIgnored = Some("feature ignored")
  private val someNoFocus = Some("no focus")
}
