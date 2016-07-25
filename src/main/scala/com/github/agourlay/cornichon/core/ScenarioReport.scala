package com.github.agourlay.cornichon.core

sealed trait ScenarioReport {
  def scenarioName: String
  def isSuccess: Boolean
  def session: Session
  def logs: Vector[LogInstruction]
}

object ScenarioReport {
  def build(scenarioName: String, session: Session, mainResult: StepsResult, finallyResult: Option[StepsResult] = None): ScenarioReport =
    finallyResult.fold {
      mainResult match {
        case SuccessStepsResult(l)    ⇒ SuccessScenarioReport(scenarioName, session, l)
        case FailureStepsResult(f, l) ⇒ FailureScenarioReport(scenarioName, Vector(f), session, l)
      }
    } { finallyRes ⇒

      (mainResult, finallyRes) match {
        // Success + Sucess = Success
        case (SuccessStepsResult(leftLogs), SuccessStepsResult(rightLogs)) ⇒
          SuccessScenarioReport(scenarioName, session, leftLogs ++ rightLogs)

        // Success + Error = Error
        case (SuccessStepsResult(leftLogs), FailureStepsResult(failedStep, rightLogs)) ⇒
          FailureScenarioReport(scenarioName, Vector(failedStep), session, leftLogs ++ rightLogs)

        // Error + Success = Error
        case (FailureStepsResult(failedStep, leftLogs), SuccessStepsResult(rightLogs)) ⇒
          FailureScenarioReport(scenarioName, Vector(failedStep), session, leftLogs ++ rightLogs)

        // Error + Error = Errors accumulated
        case (FailureStepsResult(leftFailedStep, leftLogs), FailureStepsResult(rightFailedStep, rightLogs)) ⇒
          FailureScenarioReport(scenarioName, Vector(leftFailedStep, rightFailedStep), session, leftLogs ++ rightLogs)
      }
    }
}

case class SuccessScenarioReport(scenarioName: String, session: Session, logs: Vector[LogInstruction]) extends ScenarioReport {
  val isSuccess = true
}

case class FailureScenarioReport(scenarioName: String, failedSteps: Vector[FailedStep], session: Session, logs: Vector[LogInstruction]) extends ScenarioReport {

  val isSuccess = false

  private def messageForFailedStep(failedStep: FailedStep) =
    s"""
    |${failedStep.step.title}
    |with error:
    |${failedStep.error.msg}
    |
    |""".stripMargin

  val msg =
    s"""
      |Scenario '$scenarioName' failed at step(s):
      |${failedSteps.map(messageForFailedStep).mkString("and\n")}
      | """.trim.stripMargin
}

sealed trait StepsResult {
  def logs: Vector[LogInstruction]
}

case class SuccessStepsResult(logs: Vector[LogInstruction]) extends StepsResult

case class FailedStep(step: Step, error: CornichonError)

object FailedStep {
  def fromThrowable(step: Step, error: Throwable) = FailedStep(step, CornichonError.fromThrowable(error))
}

case class FailureStepsResult(failedStep: FailedStep, logs: Vector[LogInstruction]) extends StepsResult