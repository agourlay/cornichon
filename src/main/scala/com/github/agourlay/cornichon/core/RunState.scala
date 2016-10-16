package com.github.agourlay.cornichon.core

case class RunState(remainingSteps: List[Step], session: Session, logs: Vector[LogInstruction], depth: Int) {

  def goDeeper = copy(depth = depth + 1)

  def withSteps(steps: List[Step]) = copy(remainingSteps = steps)
  def consumCurrentStep = copy(remainingSteps = remainingSteps.tail)

  def withSession(s: Session) = copy(session = s)
  def addToSession(tuples: Seq[(String, String)]) = withSession(session.addValues(tuples))
  def mergeSessions(other: Session) = copy(session = session.merge(other))

  def withLogs(logs: Vector[LogInstruction]) = copy(logs = logs)
  def withLog(log: LogInstruction) = copy(logs = Vector(log))

  def appendLog(add: LogInstruction) = copy(logs = logs :+ add)
  def appendLogs(add: Vector[LogInstruction]) = copy(logs = logs ++ add)

  def resetLogs = copy(logs = Vector.empty)

  def prependSteps(prepend: List[Step]) = copy(remainingSteps = prepend ++ remainingSteps)

}
