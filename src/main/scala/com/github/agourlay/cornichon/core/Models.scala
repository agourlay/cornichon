package com.github.agourlay.cornichon.core

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
  def run(engine: Engine, session: Session, depth: Int)(implicit ec: ExecutionContext): StepsReport
}

trait WrapperStep extends Step {
  def nested: Vector[Step]
}

