package com.github.agourlay.cornichon.core

case class RunState(remainingSteps: List[Step], session: Session, logs: Vector[LogInstruction], depth: Int) {

  lazy val goDeeper = copy(depth = depth + 1)

  lazy val resetLogs = copy(logs = Vector.empty)

  lazy val consumCurrentStep = copy(remainingSteps = remainingSteps.tail)

  def combine(other: RunState) = this.copy(
    remainingSteps = remainingSteps ++ other.remainingSteps,
    session = session.merge(other.session),
    logs = logs ++ other.logs
  )

  def withSteps(steps: List[Step]) = copy(remainingSteps = steps)
  // Helper fct to set remaining steps, go deeper and reset logs
  def forNestedSteps(steps: List[Step]) = copy(remainingSteps = steps, depth = depth + 1, logs = Vector.empty)

  def withSession(s: Session) = copy(session = s)
  def addToSession(tuples: Seq[(String, String)]) = withSession(session.addValues(tuples))
  def mergeSessions(other: Session) = copy(session = session.merge(other))

  def withLogs(logs: Vector[LogInstruction]) = copy(logs = logs)
  def withLog(log: LogInstruction) = copy(logs = Vector(log))

  // Vector concat. is not great, maybe change logs data structure
  def appendLogs(add: Vector[LogInstruction]) = copy(logs = logs ++ add)
  def appendLogsFrom(fromRun: RunState) = copy(logs = logs ++ fromRun.logs)
  def appendLog(add: LogInstruction) = copy(logs = logs :+ add)

  def prependSteps(prepend: List[Step]) = copy(remainingSteps = prepend ++ remainingSteps)

}
