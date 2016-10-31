package com.github.agourlay.cornichon.core

import cats.data.{ NonEmptyList, Xor }
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
        case Right(_)         ⇒ SuccessScenarioReport(scenarioName, session, logs)
        case Left(failedStep) ⇒ FailureScenarioReport(scenarioName, NonEmptyList.of(failedStep), session, logs)
      }
    } { finallyRes ⇒

      (mainResult, finallyRes) match {
        // Success + Success = Success
        case (Right(_), Right(_)) ⇒
          SuccessScenarioReport(scenarioName, session, logs)

        // Success + Error = Error
        case (Right(_), Left(failedStep)) ⇒
          FailureScenarioReport(scenarioName, NonEmptyList.of(failedStep), session, logs)

        // Error + Success = Error
        case (Left(failedStep), Right(_)) ⇒
          FailureScenarioReport(scenarioName, NonEmptyList.of(failedStep), session, logs)

        // Error + Error = Errors accumulated
        case (Left(leftFailedStep), Left(rightFailedStep)) ⇒
          FailureScenarioReport(scenarioName, NonEmptyList.of(leftFailedStep, rightFailedStep), session, logs)
      }
    }
}

case class SuccessScenarioReport(scenarioName: String, session: Session, logs: Vector[LogInstruction]) extends ScenarioReport {
  val isSuccess = true
}

case class FailureScenarioReport(scenarioName: String, failedSteps: NonEmptyList[FailedStep], session: Session, logs: Vector[LogInstruction]) extends ScenarioReport {

  val isSuccess = false

  private def messageForFailedStep(failedStep: FailedStep) =
    s"""
    |at step:
    |${failedStep.step.title}
    |
    |with error:
    |${failedStep.error.msg}
    |""".stripMargin

  val msg =
    s"""|Scenario '$scenarioName' failed:
        |${failedSteps.map(messageForFailedStep).toList.mkString("\nand\n")}""".stripMargin
}

sealed abstract class Done
case object Done extends Done {
  val rightDone = Xor.right(Done)
}

case class FailedStep(step: Step, error: CornichonError)

object FailedStep {
  def fromThrowable(step: Step, error: Throwable) = FailedStep(step, CornichonError.fromThrowable(error))
}