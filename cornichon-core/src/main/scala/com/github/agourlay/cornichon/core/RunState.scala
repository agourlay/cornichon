package com.github.agourlay.cornichon.core

import cats.syntax.monoid._

case class RunState(
    engine: ScenarioRunner,
    session: Session,
    logStack: List[LogInstruction], // reversed for fast appending
    depth: Int,
    cleanupSteps: List[Step]
) {
  lazy val goDeeper = copy(depth = depth + 1)
  lazy val resetLogStack = copy(logStack = Nil)

  // Helper fct to setup up a child nested context for a run which result must be merged back in using 'mergeNested'.
  // Only the session is propagated downstream as it is.
  lazy val nestedContext = copy(depth = depth + 1, logStack = Nil, cleanupSteps = Nil)
  lazy val sameLevelContext = copy(logStack = Nil, cleanupSteps = Nil)

  def withSession(s: Session) = copy(session = s)
  def addToSession(tuples: Seq[(String, String)]) = withSession(session.addValuesUnsafe(tuples: _*))
  def addToSession(key: String, value: String) = withSession(session.addValueUnsafe(key, value))
  def mergeSessions(other: Session) = copy(session = session.combine(other))

  def withLog(log: LogInstruction) = copy(logStack = log :: Nil)

  def recordLogStack(add: List[LogInstruction]) = copy(logStack = add ++ logStack)
  def recordLog(add: LogInstruction) = copy(logStack = add :: logStack)

  // Cleanups steps are added in the opposite order
  def registerCleanupStep(add: Step) = copy(cleanupSteps = add :: cleanupSteps)
  def registerCleanupSteps(add: List[Step]) = copy(cleanupSteps = add ::: cleanupSteps)
  lazy val resetCleanupSteps = copy(cleanupSteps = Nil)

  // Helpers to propagate info from nested computation
  def mergeNested(r: RunState): RunState = mergeNested(r, r.logStack)

  def mergeNested(r: RunState, extraLogStack: List[LogInstruction]): RunState =
    copy(
      session = r.session, // no need to combine, nested session is built on top of the initial one
      cleanupSteps = r.cleanupSteps ::: this.cleanupSteps, // prepend cleanup steps
      logStack = extraLogStack ++ this.logStack // logs are often built manually and not extracted from RunState
    )
}