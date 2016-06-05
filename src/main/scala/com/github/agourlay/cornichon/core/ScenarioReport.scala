package com.github.agourlay.cornichon.core

trait ScenarioReport {
  def scenarioName: String
  def isSuccess: Boolean
  def session: Session
  def logs: Vector[LogInstruction]
}

object ScenarioReport {
  def build(scenarioName: String, stepsResult: StepsResult*): ScenarioReport = mergeReport(stepsResult) match {
    case SuccessStepsResult(s, l)    ⇒ SuccessScenarioReport(scenarioName, s, l)
    case FailureStepsResult(f, s, l) ⇒ FailureScenarioReport(scenarioName, f, s, l)
  }

  def mergeReport(stepsResults: Seq[StepsResult]) = {
    stepsResults.reduceLeft { (acc, nextResult) ⇒
      (acc, nextResult) match {
        // Success + Sucess = Success
        case (SuccessStepsResult(leftSession, leftLogs), SuccessStepsResult(rightSession, rightLogs)) ⇒
          SuccessStepsResult(leftSession.merge(rightSession), leftLogs ++ rightLogs)
        // Success + Error = Error
        case (SuccessStepsResult(leftSession, leftLogs), FailureStepsResult(failedStep, rightSession, rightLogs)) ⇒
          FailureStepsResult(failedStep, leftSession.merge(rightSession), leftLogs ++ rightLogs)
        // Error + Success = Error
        case (FailureStepsResult(failedStep, leftSession, leftLogs), SuccessStepsResult(rightSession, rightLogs)) ⇒
          FailureStepsResult(failedStep, leftSession.merge(rightSession), leftLogs ++ rightLogs)
        // Error + Error = Error
        case (FailureStepsResult(leftFailedStep, leftSession, leftLogs), FailureStepsResult(rightFailedStep, rightSession, rightLogs)) ⇒
          FailureStepsResult(leftFailedStep, leftSession.merge(rightSession), leftLogs ++ rightLogs)
      }
    }
  }
}

case class SuccessScenarioReport(scenarioName: String, session: Session, logs: Vector[LogInstruction]) extends ScenarioReport {
  val isSuccess = true
}

case class FailureScenarioReport(scenarioName: String, failedStep: FailedStep, session: Session, logs: Vector[LogInstruction]) extends ScenarioReport {

  val isSuccess = false

  val msg = s"""
      |
      |Scenario '$scenarioName' failed at step:
      |${failedStep.step.title}
      |with error:
      |${failedStep.error.msg}
      | """.trim.stripMargin
}

sealed trait StepsResult {
  def logs: Vector[LogInstruction]
  def session: Session
  def isSuccess: Boolean
}

case class SuccessStepsResult(session: Session, logs: Vector[LogInstruction]) extends StepsResult {
  val isSuccess = true
}

case class FailedStep(step: Step, error: CornichonError)

object FailedStep {
  def fromThrowable(step: Step, error: Throwable) =
    FailedStep(step, CornichonError.fromThrowable(error))
}

case class FailureStepsResult(failedStep: FailedStep, session: Session, logs: Vector[LogInstruction]) extends StepsResult {
  val isSuccess = false
}