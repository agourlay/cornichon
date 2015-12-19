package com.github.agourlay.cornichon.core

import scala.language.existentials

sealed trait StepsReport

case class SuccessRunSteps(session: Session, logs: Vector[LogInstruction]) extends StepsReport
case class FailedRunSteps(failedStep: FailedStep, notExecutedStep: Vector[String], logs: Vector[LogInstruction]) extends StepsReport

sealed trait ScenarioReport {
  val scenarioName: String
  val success: Boolean
  val logs: Seq[LogInstruction]
}

case class SuccessScenarioReport(scenarioName: String, successSteps: Vector[String], logs: Vector[LogInstruction]) extends ScenarioReport {
  val success = true
}

object SuccessScenarioReport {
  def apply(scenario: Scenario, stepsRun: SuccessRunSteps): SuccessScenarioReport = {
    val successSteps = scenario.steps.collect { case RunnableStep(title, _, _, _) ⇒ title }
    SuccessScenarioReport(scenario.name, successSteps, stepsRun.logs)
  }
}

case class FailedScenarioReport(scenarioName: String, failedStep: FailedStep, successSteps: Vector[String], notExecutedStep: Vector[String], logs: Vector[LogInstruction]) extends ScenarioReport {
  val success = false
  val msg = s"""
    |
    |Scenario "$scenarioName" failed at step
    |${failedStep.step.title} with error:
    |${failedStep.error.msg}
    | """.trim.stripMargin
}

object FailedScenarioReport {
  def apply(scenario: Scenario, stepsRun: FailedRunSteps): FailedScenarioReport = {
    val successSteps = scenario.steps.takeWhile(_ != stepsRun.failedStep.step).collect { case RunnableStep(title, _, _, _) ⇒ title }
    FailedScenarioReport(scenario.name, stepsRun.failedStep, successSteps, stepsRun.notExecutedStep, stepsRun.logs)
  }
}

case class FailedStep(step: Step, error: CornichonError)

sealed trait LogInstruction {
  val message: String
  val margin: Int
}

case class DefaultLogInstruction(message: String, margin: Int) extends LogInstruction
case class ColoredLogInstruction(message: String, ansiColor: String, margin: Int) extends LogInstruction
