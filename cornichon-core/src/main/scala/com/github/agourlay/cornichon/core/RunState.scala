package com.github.agourlay.cornichon.core

import cats.data.Chain
import cats.syntax.monoid._

case class RunState(
    session: Session,
    logs: Chain[LogInstruction],
    depth: Int,
    cleanupSteps: List[Step]
) {

  lazy val goDeeper = copy(depth = depth + 1)
  lazy val resetLogs = copy(logs = Chain.empty)

  // Helper fct to setup up a child nested context for a run which result must be merged back in using 'mergeNested'.
  // Only the session is propagated downstream as it is.
  lazy val nestedContext = copy(depth = depth + 1, logs = Chain.empty, cleanupSteps = Nil)
  lazy val sameLevelContext = copy(logs = Chain.empty, cleanupSteps = Nil)

  def withSession(s: Session) = copy(session = s)
  def addToSession(tuples: Seq[(String, String)]) = withSession(session.addValuesUnsafe(tuples: _*))
  def addToSession(key: String, value: String) = withSession(session.addValueUnsafe(key, value))
  def mergeSessions(other: Session) = copy(session = session.combine(other))

  def withLog(log: LogInstruction) = copy(logs = Chain.one(log))

  def appendLogs(add: Chain[LogInstruction]) = copy(logs = logs ++ add)
  def appendLogsFrom(from: RunState) = appendLogs(from.logs)
  def appendLog(add: LogInstruction) = copy(logs = logs :+ add)

  // Cleanups steps are added in the opposite order
  def prependCleanupStep(add: Step) = copy(cleanupSteps = add :: cleanupSteps)
  def prependCleanupSteps(add: List[Step]) = copy(cleanupSteps = add ::: cleanupSteps)
  def prependCleanupStepsFrom(from: RunState) = copy(cleanupSteps = from.cleanupSteps ::: cleanupSteps)
  lazy val resetCleanupSteps = copy(cleanupSteps = Nil)

  // Helpers to propagate info from nested computation
  def mergeNested(r: RunState): RunState = mergeNested(r, r.logs)
  def mergeNested(r: RunState, computationLogs: Chain[LogInstruction]): RunState =
    this.copy(
      session = r.session, // no need to combine, nested session is built on top of the initial one
      cleanupSteps = r.cleanupSteps ::: this.cleanupSteps, // prepend cleanup steps
      logs = this.logs ++ computationLogs // logs are often built manually and not extracted from RunState
    )
}