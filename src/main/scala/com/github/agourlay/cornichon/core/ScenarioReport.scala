package com.github.agourlay.cornichon.core

import scala.language.existentials

sealed trait StepsReport {
  val logs: Vector[LogInstruction]
  val session: Session
}

case class SuccessRunSteps(session: Session, logs: Vector[LogInstruction]) extends StepsReport
case class FailedRunSteps(failedStep: FailedStep, notExecutedStep: Vector[String], logs: Vector[LogInstruction], session: Session) extends StepsReport

sealed trait ScenarioReport {
  val scenarioName: String
  val success: Boolean
  val logs: Seq[LogInstruction]
  val session: Session
  def merge(scenarioReport: ScenarioReport): ScenarioReport
}

case class SuccessScenarioReport(scenarioName: String, successSteps: Vector[String], logs: Vector[LogInstruction], session: Session) extends ScenarioReport {
  val success = true
  def merge(scenarioReport: ScenarioReport) = scenarioReport match {
    case SuccessScenarioReport(_, otherSuccessSteps, otherLogs, _) ⇒
      this.copy(successSteps = successSteps ++ otherSuccessSteps, logs = logs ++ otherLogs)
    case f @ FailedScenarioReport(_, _, otherSuccessSteps, notExecutedStep, otherLogs, _) ⇒
      // The FailedReport is returned with the success metadata prepended
      f.copy(successSteps = successSteps ++ otherSuccessSteps, logs = logs ++ otherLogs)
  }
}

object SuccessScenarioReport {
  def apply(scenario: Scenario, stepsRun: SuccessRunSteps): SuccessScenarioReport = {
    val successSteps = scenario.steps.collect { case RunnableStep(title, _, _, _) ⇒ title }
    SuccessScenarioReport(scenario.name, successSteps, stepsRun.logs, stepsRun.session)
  }
}

case class FailedScenarioReport(scenarioName: String, failedStep: FailedStep, successSteps: Vector[String], notExecutedStep: Vector[String], logs: Vector[LogInstruction], session: Session) extends ScenarioReport {
  val success = false
  val msg = s"""
    |
    |Scenario "$scenarioName" failed at step
    |${failedStep.step.title} with error:
    |${failedStep.error.msg}
    | """.trim.stripMargin

  def merge(scenarioReport: ScenarioReport) = scenarioReport match {
    case s @ SuccessScenarioReport(_, otherSuccessSteps, otherLogs, _) ⇒
      this.copy(successSteps = successSteps ++ otherSuccessSteps, logs = logs ++ otherLogs)
    case f @ FailedScenarioReport(_, _, otherSuccessSteps, _, otherLogs, _) ⇒
      // The first failure will be used to trigger the error, the second error will be present in the logs after
      this.copy(successSteps = successSteps ++ otherSuccessSteps, logs = logs ++ otherLogs)
  }
}

object FailedScenarioReport {
  def apply(scenario: Scenario, stepsRun: FailedRunSteps): FailedScenarioReport = {
    val successSteps = scenario.steps.takeWhile(_ != stepsRun.failedStep.step).collect { case RunnableStep(title, _, _, _) ⇒ title }
    FailedScenarioReport(scenario.name, stepsRun.failedStep, successSteps, stepsRun.notExecutedStep, stepsRun.logs, stepsRun.session)
  }
}

case class FailedStep(step: Step, error: CornichonError)

sealed trait LogInstruction {
  val message: String
  val margin: Int
}

case class DefaultLogInstruction(message: String, margin: Int) extends LogInstruction
case class ColoredLogInstruction(message: String, ansiColor: String, margin: Int) extends LogInstruction
