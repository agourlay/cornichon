package com.github.agourlay.cornichon.core

sealed trait ScenarioReport {
  def scenarioName: String
  def isSuccess: Boolean
  def session: Session
  def logs: Vector[LogInstruction]
}

object ScenarioReport {
  def build(scenarioName: String, mainResult: StepsResult, finallyResult: Option[StepsResult] = None): ScenarioReport =
    finallyResult.fold {
      mainResult match {
        case SuccessStepsResult(s, l)    ⇒ SuccessScenarioReport(scenarioName, s, l)
        case FailureStepsResult(f, s, l) ⇒ FailureScenarioReport(scenarioName, Vector(f), s, l)
      }
    } { finallyRes ⇒

      (mainResult, finallyRes) match {
        // Success + Sucess = Success
        case (SuccessStepsResult(leftSession, leftLogs), SuccessStepsResult(rightSession, rightLogs)) ⇒
          SuccessScenarioReport(scenarioName, leftSession.merge(rightSession), leftLogs ++ rightLogs)

        // Success + Error = Error
        case (SuccessStepsResult(leftSession, leftLogs), FailureStepsResult(failedStep, rightSession, rightLogs)) ⇒
          FailureScenarioReport(scenarioName, Vector(failedStep), leftSession.merge(rightSession), leftLogs ++ rightLogs)

        // Error + Success = Error
        case (FailureStepsResult(failedStep, leftSession, leftLogs), SuccessStepsResult(rightSession, rightLogs)) ⇒
          FailureScenarioReport(scenarioName, Vector(failedStep), leftSession.merge(rightSession), leftLogs ++ rightLogs)

        // Error + Error = Errors accumulated
        case (FailureStepsResult(leftFailedStep, leftSession, leftLogs), FailureStepsResult(rightFailedStep, rightSession, rightLogs)) ⇒
          FailureScenarioReport(scenarioName, Vector(leftFailedStep, rightFailedStep), leftSession.merge(rightSession), leftLogs ++ rightLogs)
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