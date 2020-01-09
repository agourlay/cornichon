package com.github.agourlay.cornichon.core

import cats.syntax.monoid._
import com.github.agourlay.cornichon.matchers.{ Matcher, MatcherResolver }
import com.github.agourlay.cornichon.resolver.{ Mapper, PlaceholderResolver, Resolvable }

case class RunState(
    customExtractors: Map[String, Mapper],
    allMatchers: Map[String, List[Matcher]],
    randomContext: RandomContext,
    session: Session,
    logStack: List[LogInstruction], // reversed for fast appending
    depth: Int,
    cleanupSteps: List[Step]
) { rs =>
  lazy val goDeeper: RunState = copy(depth = depth + 1)
  lazy val resetLogStack: RunState = copy(logStack = Nil)

  // Helper fct to setup up a child nested context for a run which result must be merged back in using 'mergeNested'.
  // Only the session is propagated downstream as it is.
  lazy val nestedContext: RunState = copy(depth = depth + 1, logStack = Nil, cleanupSteps = Nil)
  lazy val sameLevelContext: RunState = copy(logStack = Nil, cleanupSteps = Nil)

  def withSession(s: Session): RunState = copy(session = s)
  def addToSession(tuples: Seq[(String, String)]): RunState = withSession(session.addValuesUnsafe(tuples: _*))
  def addToSession(key: String, value: String): RunState = withSession(session.addValueUnsafe(key, value))
  def mergeSessions(other: Session): RunState = copy(session = session.combine(other))

  def withLog(log: LogInstruction): RunState = copy(logStack = log :: Nil)
  def recordLogStack(add: List[LogInstruction]): RunState = copy(logStack = add ++ logStack)
  def recordLog(add: LogInstruction): RunState = copy(logStack = add :: logStack)

  // Cleanups steps are added in the opposite order
  def registerCleanupStep(add: Step): RunState = copy(cleanupSteps = add :: cleanupSteps)
  def registerCleanupSteps(add: List[Step]): RunState = copy(cleanupSteps = add ::: cleanupSteps)
  lazy val resetCleanupSteps: RunState = copy(cleanupSteps = Nil)

  // Helpers to propagate info from nested computation
  def mergeNested(r: RunState): RunState = mergeNested(r, r.logStack)
  def mergeNested(r: RunState, extraLogStack: List[LogInstruction]): RunState =
    copy(
      randomContext = r.randomContext, // nested randomContext is built on top of the initial one
      session = r.session, // no need to combine, nested session is built on top of the initial one
      cleanupSteps = r.cleanupSteps ::: this.cleanupSteps, // prepend cleanup steps
      logStack = extraLogStack ++ this.logStack // logs are often built manually and not extracted from RunState
    )

  lazy val scenarioContext: ScenarioContext = new ScenarioContext {
    val randomContext: RandomContext = rs.randomContext
    val session: Session = rs.session

    def fillPlaceholders[A: Resolvable](input: A): Either[CornichonError, A] =
      PlaceholderResolver.fillPlaceholdersResolvable(input)(session, randomContext, customExtractors)

    def fillPlaceholders(input: String): Either[CornichonError, String] =
      PlaceholderResolver.fillPlaceholders(input)(session, randomContext, customExtractors)

    def fillSessionPlaceholders(input: String): Either[CornichonError, String] =
      PlaceholderResolver.fillPlaceholders(input)(session, randomContext, customExtractors, sessionOnlyMode = true)

    def fillPlaceholders(params: Seq[(String, String)]): Either[CornichonError, List[(String, String)]] =
      PlaceholderResolver.fillPlaceholdersMany(params)(session, randomContext, customExtractors)

    def findAllMatchers(input: String): Either[CornichonError, List[Matcher]] =
      MatcherResolver.findAllMatchers(allMatchers)(input)
  }

}

object RunState {
  def fromFeatureContext(
    featureContext: FeatureContext,
    session: Session,
    logStack: List[LogInstruction],
    depth: Int,
    cleanupSteps: List[Step]): RunState =
    RunState(
      featureContext.customExtractors,
      featureContext.allMatchers,
      RandomContext.fromOptSeed(featureContext.withSeed),
      session,
      logStack,
      depth,
      cleanupSteps)
}