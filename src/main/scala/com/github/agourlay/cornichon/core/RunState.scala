package com.github.agourlay.cornichon.core

case class RunState(remainingSteps: Vector[Step], session: Session, logs: Vector[LogInstruction], depth: Int) {

  lazy val endReached = remainingSteps.isEmpty

  lazy val currentStep = remainingSteps.head

  def goDeeper = copy(depth = depth + 1)

  def withSteps(steps: Vector[Step]) = copy(remainingSteps = steps)
  def consumCurrentStep = copy(remainingSteps = remainingSteps.drop(1))

  def withSession(s: Session) = copy(session = s)
  def addToSession(tuples: Seq[(String, String)]) = withSession(session.addValues(tuples))

  def withLogs(logs: Vector[LogInstruction]) = copy(logs = logs)
  def withLog(log: LogInstruction) = copy(logs = Vector(log))

  def appendLog(add: LogInstruction) = copy(logs = logs :+ add)
  def appendLogs(add: Vector[LogInstruction]) = copy(logs = logs ++ add)

  def prependLog(add: LogInstruction) = copy(logs = add +: logs)
  def prependLogs(add: Vector[LogInstruction]) = copy(logs = add ++ logs)

  def resetLogs = copy(logs = Vector.empty)

  def prependSteps(prepend: Vector[Step]) = copy(remainingSteps = prepend ++ remainingSteps)

}
