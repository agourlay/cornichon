package com.github.agourlay.cornichon.core

trait ScenarioReport {
  val scenarioName: String
  val success: Boolean
}
case class SuccessScenarioReport(scenarioName: String, successSteps: Seq[String]) extends ScenarioReport {
  val success = true
}
case class FailedScenarioReport(scenarioName: String, failedStep: FailedStep, successSteps: Seq[String], notExecutedStep: Seq[String]) extends ScenarioReport {
  val success = false
  val msg = s"""
    |
    |Scenario "$scenarioName" failed at step "${failedStep.step}" with error:
    |${failedStep.error.msg}
    | """.trim.stripMargin
}

case class FailedStep(step: String, error: CornichonError)