package com.github.agourlay.cornichon.core

import cats.data.{ NonEmptyList, ValidatedNel, Xor }
import cats.kernel.Semigroup

sealed trait ScenarioReport {
  def isSuccess: Boolean
  def session: Session
  def logs: Vector[LogInstruction]
}

object ScenarioReport {
  def build(scenarioName: String, runState: RunState, result: ValidatedNel[FailedStep, Done]): ScenarioReport =
    result.fold(
      failedSteps ⇒ FailureScenarioReport(scenarioName, failedSteps, runState.session, runState.logs),
      _ ⇒ SuccessScenarioReport(scenarioName, runState.session, runState.logs)
    )
}

case class SuccessScenarioReport(scenarioName: String, session: Session, logs: Vector[LogInstruction]) extends ScenarioReport {
  val isSuccess = true
}

case class FailureScenarioReport(scenarioName: String, failedSteps: NonEmptyList[FailedStep], session: Session, logs: Vector[LogInstruction]) extends ScenarioReport {

  val isSuccess = false

  val msg =
    s"""|Scenario '$scenarioName' failed:
        |${failedSteps.map(_.messageForFailedStep).toList.mkString("\nand\n")}""".stripMargin
}

sealed abstract class Done
case object Done extends Done {
  val rightDone = Xor.right(Done)
  implicit val semigroup = new Semigroup[Done] {
    def combine(x: Done, y: Done): Done = x
  }
}

case class FailedStep(step: Step, error: CornichonError) {
  val messageForFailedStep =
    s"""
       |at step:
       |${step.title}
       |
       |with error:
       |${error.msg}
       |""".stripMargin

}

object FailedStep {
  def fromThrowable(step: Step, error: Throwable) = FailedStep(step, CornichonError.fromThrowable(error))
}