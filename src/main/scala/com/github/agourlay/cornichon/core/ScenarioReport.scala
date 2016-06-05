package com.github.agourlay.cornichon.core


case class ScenarioReport(scenarioName: String, stepsExecutionResult: StepsResult) {

  val msg = stepsExecutionResult match {
    case s: SuccessStepsResult ⇒
      s"Scenario '$scenarioName' succeeded"

    case FailureStepsResult(failedStep, _, _) ⇒
      s"""
      |
      |Scenario '$scenarioName' failed at step:
      |${failedStep.step.title}
      |with error:
      |${failedStep.error.msg}
      | """.trim.stripMargin
  }
}

sealed trait StepsResult {
  def logs: Vector[LogInstruction]
  def session: Session
  def isSuccess: Boolean
  def merge(otherStepsReport: StepsResult): StepsResult
}

case class SuccessStepsResult(session: Session, logs: Vector[LogInstruction]) extends StepsResult {
  val isSuccess = true

  def merge(otherStepsResult: StepsResult) = otherStepsResult match {
    case s: SuccessStepsResult ⇒
      // Success + Sucess = Success
      SuccessStepsResult(session.merge(otherStepsResult.session), logs ++ otherStepsResult.logs)
    case f: FailureStepsResult ⇒
      // Success + Error = Error
      f.copy(session = session.merge(otherStepsResult.session), logs = logs ++ otherStepsResult.logs)
  }
}

case class FailedStep(step: Step, error: CornichonError)

object FailedStep {
  def fromThrowable(step: Step, error: Throwable) =
    FailedStep(step, CornichonError.fromThrowable(error))
}

case class FailureStepsResult(failedStep: FailedStep, logs: Vector[LogInstruction], session: Session) extends StepsResult {
  val isSuccess = false

  def merge(otherStepRunReport: StepsResult) = otherStepRunReport match {
    case s: SuccessStepsResult ⇒
      // Error + Success = Error
      this.copy(session = session.merge(otherStepRunReport.session), logs = logs ++ otherStepRunReport.logs)
    case f: FailureStepsResult ⇒
      // Error + Error = Error
      f.copy(session = session.merge(otherStepRunReport.session), logs = logs ++ otherStepRunReport.logs)
  }
}