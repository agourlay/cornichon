package com.github.agourlay.cornichon.core

sealed trait ScenarioReport {
  val scenarioName: String
  val success: Boolean
  val logs: Seq[LogInstruction]
}

case class SuccessScenarioReport(scenarioName: String, successSteps: Seq[String], logs: Seq[LogInstruction]) extends ScenarioReport {
  val success = true
}

case class FailedScenarioReport(scenarioName: String, failedStep: FailedStep, successSteps: Seq[String], notExecutedStep: Seq[String], logs: Seq[LogInstruction]) extends ScenarioReport {
  val success = false
  val msg = s"""
    |
    |Scenario "$scenarioName" failed at step "${failedStep.step}" with error:
    |${failedStep.error.msg}
    | """.trim.stripMargin
}

case class FailedStep(step: String, error: CornichonError)

sealed trait LogInstruction {
  val message: String
}

case class DefaultLogInstruction(message: String) extends LogInstruction
case class ColoredLogInstruction(message: String, ansiColor: String) extends LogInstruction
