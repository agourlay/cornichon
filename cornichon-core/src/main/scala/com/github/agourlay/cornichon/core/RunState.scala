package com.github.agourlay.cornichon.core

import cats.instances.list._
import cats.instances.vector._

import cats.kernel.Monoid
import cats.syntax.monoid._

case class RunState(
    session: Session,
    logs: Vector[LogInstruction],
    depth: Int,
    cleanupSteps: List[Step]
) {

  lazy val goDeeper = copy(depth = depth + 1)
  lazy val resetLogs = copy(logs = Vector.empty)
  // Helper fct to go deeper and reset logs in one copy.
  lazy val nestedContext = copy(depth = depth + 1, logs = Vector.empty)

  def withSession(s: Session) = copy(session = s)
  def addToSession(tuples: Seq[(String, String)]) = withSession(session.addValuesUnsafe(tuples: _*))
  def addToSession(key: String, value: String) = withSession(session.addValueUnsafe(key, value))
  def mergeSessions(other: Session) = copy(session = session.combine(other))

  def withLogs(logs: Vector[LogInstruction]) = copy(logs = logs)
  def withLog(log: LogInstruction) = copy(logs = Vector(log))

  // Vector concat. is not great, maybe change logs data structure
  def appendLogs(add: Vector[LogInstruction]) = copy(logs = logs ++ add)
  def appendLogsFrom(from: RunState) = appendLogs(from.logs)
  def appendLog(add: LogInstruction) = copy(logs = logs :+ add)

  // Cleanups steps are added in the opposite order
  def prependCleanupStep(add: Step) = copy(cleanupSteps = add :: cleanupSteps)
  def prependCleanupSteps(add: List[Step]) = copy(cleanupSteps = add ::: cleanupSteps)
  def prependCleanupStepsFrom(from: RunState) = copy(cleanupSteps = from.cleanupSteps ::: cleanupSteps)

  // Helpers to propagate info from nested computation
  def mergeNested(r: RunState): RunState = mergeNested(r, r.logs)
  def mergeNested(r: RunState, computationLogs: Vector[LogInstruction]): RunState =
    this.copy(
      session = r.session, // no need to combine, nested session is built on top of the initial one
      cleanupSteps = r.cleanupSteps ::: this.cleanupSteps, // prepend cleanup steps
      logs = this.logs ++ computationLogs // logs are often built manually and not extracted from RunState
    )
}

object RunState {

  implicit val monoidRunState = new Monoid[RunState] {
    def empty: RunState = RunState(Session.newEmpty, Vector.empty, 1, Nil)
    def combine(x: RunState, y: RunState): RunState = x.copy(
      session = x.session.combine(y.session),
      logs = x.logs.combine(y.logs),
      cleanupSteps = x.cleanupSteps.combine(y.cleanupSteps)
    )
  }
}