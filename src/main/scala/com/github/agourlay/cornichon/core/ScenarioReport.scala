package com.github.agourlay.cornichon.core

import cats.data.Xor
import cats.data.Xor._

sealed trait ScenarioReport {
  def scenarioName: String
  def isSuccess: Boolean
  def session: Session
  def logs: Vector[LogInstruction]
}

object ScenarioReport {
  def build(scenarioName: String, session: Session, logs: Vector[LogInstruction], mainResult: Xor[FailedStep, Done], finallyResult: Option[Xor[FailedStep, Done]] = None): ScenarioReport =
    finallyResult.fold {
      mainResult match {
        case Right(done)      ⇒ SuccessScenarioReport(scenarioName, session, logs)
        case Left(failedStep) ⇒ FailureScenarioReport(scenarioName, Vector(failedStep), session, logs)
      }
    } { finallyRes ⇒

      (mainResult, finallyRes) match {
        // Success + Success = Success
        case (Right(_), Right(_)) ⇒
          SuccessScenarioReport(scenarioName, session, logs)

        // Success + Error = Error
        case (Right(_), Left(failedStep)) ⇒
          FailureScenarioReport(scenarioName, Vector(failedStep), session, logs)

        // Error + Success = Error
        case (Left(failedStep), Right(_)) ⇒
          FailureScenarioReport(scenarioName, Vector(failedStep), session, logs)

        // Error + Error = Errors accumulated
        case (Left(leftFailedStep), Left(rightFailedStep)) ⇒
          FailureScenarioReport(scenarioName, Vector(leftFailedStep, rightFailedStep), session, logs)
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

sealed abstract class Done
case object Done extends Done {
  val rightDone = Xor.right(Done)
}

case class FailedStep(step: Step, error: CornichonError)

object FailedStep {
  def fromThrowable(step: Step, error: Throwable) = FailedStep(step, CornichonError.fromThrowable(error))
}