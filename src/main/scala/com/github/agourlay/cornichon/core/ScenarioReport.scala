package com.github.agourlay.cornichon.core

import scala.language.existentials

sealed trait StepsReport

case class SuccessRunSteps(logs: Seq[LogInstruction]) extends StepsReport
case class FailedRunSteps(failedStep: FailedStep, notExecutedStep: Seq[String], logs: Seq[LogInstruction]) extends StepsReport

sealed trait ScenarioReport {
  val scenarioName: String
  val success: Boolean
  val logs: Seq[LogInstruction]
}

case class SuccessScenarioReport(scenarioName: String, successSteps: Seq[String], logs: Seq[LogInstruction]) extends ScenarioReport {
  val success = true
}

object SuccessScenarioReport {
  def apply(scenario: Scenario, stepsRun: SuccessRunSteps): SuccessScenarioReport = {
    val successSteps = scenario.steps.collect { case ExecutableStep(title, _, _, _) ⇒ title }
    SuccessScenarioReport(scenario.name, successSteps, stepsRun.logs)
  }
}

case class FailedScenarioReport(scenarioName: String, failedStep: FailedStep, successSteps: Seq[String], notExecutedStep: Seq[String], logs: Seq[LogInstruction]) extends ScenarioReport {
  val success = false
  val msg = s"""
    |
    |Scenario "$scenarioName" failed at step "${failedStep.step.title}" with error:
    |${failedStep.error.msg}
    | """.trim.stripMargin
}

object FailedScenarioReport {
  def apply(scenario: Scenario, stepsRun: FailedRunSteps): FailedScenarioReport = {
    val successSteps = scenario.steps.takeWhile(_ != stepsRun.failedStep.step).collect { case ExecutableStep(title, _, _, _) ⇒ title }
    FailedScenarioReport(scenario.name, stepsRun.failedStep, successSteps, stepsRun.notExecutedStep, stepsRun.logs)
  }
}

case class FailedStep(step: ExecutableStep[_], error: CornichonError)

sealed trait LogInstruction {
  val message: String
}

case class DefaultLogInstruction(message: String) extends LogInstruction
case class ColoredLogInstruction(message: String, ansiColor: String) extends LogInstruction
