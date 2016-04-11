package com.github.agourlay.cornichon.core

import scala.concurrent.duration.Duration
import scala.Console._

sealed trait StepsReport {
  def logs: Vector[LogInstruction]
  def session: Session
  def isSuccess: Boolean
  def merge(otherStepsReport: StepsReport): StepsReport
}

case class SuccessRunSteps(session: Session, logs: Vector[LogInstruction]) extends StepsReport {
  val isSuccess = true

  def merge(otherStepRunReport: StepsReport) = otherStepRunReport match {
    case SuccessRunSteps(newSession, updatedLogs) ⇒
      SuccessRunSteps(session.merge(otherStepRunReport.session), logs ++ otherStepRunReport.logs)
    case f @ FailedRunSteps(_, _, failedRunLogs, _) ⇒
      // Success + Error = Error
      f.copy(session = session.merge(otherStepRunReport.session), logs = logs ++ otherStepRunReport.logs)
  }

}
case class FailedRunSteps(step: Step, error: CornichonError, logs: Vector[LogInstruction], session: Session) extends StepsReport {
  val isSuccess = false

  def merge(otherStepRunReport: StepsReport) = otherStepRunReport match {
    case SuccessRunSteps(newSession, updatedLogs) ⇒
      SuccessRunSteps(session.merge(otherStepRunReport.session), logs ++ otherStepRunReport.logs)
    case f @ FailedRunSteps(_, _, failedRunLogs, _) ⇒
      // Error + Error = Error
      f.copy(session = session.merge(otherStepRunReport.session), logs = logs ++ otherStepRunReport.logs)
  }
}

object FailedRunSteps {
  def apply(step: Step, error: Throwable, logs: Vector[LogInstruction], session: Session): FailedRunSteps = {
    val e = CornichonError.fromThrowable(error)
    FailedRunSteps(step, e, logs, session)
  }
}

case class ScenarioReport(scenarioName: String, stepsRunReport: StepsReport) {

  val msg = stepsRunReport match {
    case s: SuccessRunSteps ⇒
      s"""Scenario "$scenarioName" succeeded"""

    case FailedRunSteps(failedStep, error, _, _) ⇒
      s"""
      |
      |Scenario "$scenarioName" failed at step
      |"${failedStep.title}" with error:
      |${error.msg}
      | """.trim.stripMargin
  }
}

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