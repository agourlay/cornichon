package com.github.agourlay.cornichon.core

import cats.data.Xor

import scala.concurrent.ExecutionContext

case class FeatureDef(name: String, scenarios: Vector[Scenario], ignored: Boolean = false) {
  require(
    scenarios.map(s ⇒ s.name).distinct.length == scenarios.length,
    s"Scenarios name must be unique within a Feature - error caused by duplicated declaration of scenarios '${scenarios.map(s ⇒ s.name).diff(scenarios.map(s ⇒ s.name).distinct).mkString(", ")}'"
  )
}

case class Scenario(name: String, steps: Vector[Step], ignored: Boolean = false)

sealed trait StepAssertion[A] {
  def isSuccess: Boolean
}

case class SimpleStepAssertion[A](expected: A, result: A) extends StepAssertion[A] {
  val isSuccess = expected == result
}

case class DetailedStepAssertion[A](expected: A, result: A, details: A ⇒ String) extends StepAssertion[A] {
  val isSuccess = expected == result
}

trait Step {
  def title: String
  def run(engine: Engine)(initialRunState: RunState)(implicit ec: ExecutionContext): (RunState, FailedStep Xor Done)
}

trait WrapperStep extends Step {
  def nested: Vector[Step]

  def failedTitleLog(depth: Int) = FailureLogInstruction(title, depth)
  def successTitleLog(depth: Int) = SuccessLogInstruction(title, depth)
}

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