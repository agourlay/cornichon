package com.github.agourlay.cornichon.core

import scala.concurrent.duration.Duration
import scala.Console._

sealed trait StepsReport {
  def logs: Vector[LogInstruction]
  def session: Session
  def isSuccess: Boolean
}

case class SuccessRunSteps(session: Session, logs: Vector[LogInstruction]) extends StepsReport {
  val isSuccess = true
}
case class FailedRunSteps(failedStep: FailedStep, notExecutedStep: Vector[String], logs: Vector[LogInstruction], session: Session) extends StepsReport {
  val isSuccess = false
}

sealed trait ScenarioReport {
  def scenarioName: String
  def success: Boolean
  def logs: Seq[LogInstruction]
  def session: Session
  def merge(scenarioReport: ScenarioReport): ScenarioReport
}

object ScenarioReport {
  def fromStepsReport(scenario: Scenario, stepsReport: StepsReport) = stepsReport match {
    case s @ SuccessRunSteps(_, _)      ⇒ SuccessScenarioReport(scenario, s)
    case f @ FailedRunSteps(_, _, _, _) ⇒ FailedScenarioReport(scenario, f)
  }
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
    val successStepsTitle = scenario.steps.filterNot(_.isInstanceOf[WrapperStep]).map(_.title)
    SuccessScenarioReport(scenario.name, successStepsTitle, stepsRun.logs, stepsRun.session)
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
    val successSteps = scenario.steps.takeWhile(_ != stepsRun.failedStep.step).filterNot(_.isInstanceOf[WrapperStep]).map(_.title)
    FailedScenarioReport(scenario.name, stepsRun.failedStep, successSteps, stepsRun.notExecutedStep, stepsRun.logs, stepsRun.session)
  }
}

case class FailedStep(step: Step, error: CornichonError)

sealed trait LogInstruction {
  def message: String
  def margin: Int
  def color: String
  def duration: Option[Duration]
}

case class DefaultLogInstruction(message: String, margin: Int, duration: Option[Duration] = None) extends LogInstruction {
  val color = WHITE
}

case class SuccessLogInstruction(message: String, margin: Int, duration: Option[Duration] = None) extends LogInstruction {
  val color = GREEN
}

case class FailureLogInstruction(message: String, margin: Int, duration: Option[Duration] = None) extends LogInstruction {
  val color = RED
}

case class InfoLogInstruction(message: String, margin: Int, duration: Option[Duration] = None) extends LogInstruction {
  val color = CYAN
}