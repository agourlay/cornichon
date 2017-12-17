package com.github.agourlay.cornichon.core

import cats.data.Validated.Valid
import cats.data.{ NonEmptyList, ValidatedNel }
import cats.kernel.Monoid
import com.github.agourlay.cornichon.core.ScenarioReport._
import monix.eval.Task

import scala.concurrent.Future
import scala.concurrent.duration.{ Duration, FiniteDuration }

sealed trait ScenarioReport {
  def isSuccess: Boolean
  def scenarioName: String
  def session: Session
  def logs: Vector[LogInstruction]
  def duration: FiniteDuration
}

object ScenarioReport {
  def build(scenarioName: String, runState: RunState, result: ValidatedNel[FailedStep, Done], duration: FiniteDuration): ScenarioReport =
    result.fold(
      failedSteps ⇒ FailureScenarioReport(scenarioName, failedSteps, runState.session, runState.logs, duration),
      _ ⇒ SuccessScenarioReport(scenarioName, runState.session, runState.logs, duration)
    )
  val emptyLogs = Vector.empty[LogInstruction]
}

case class SuccessScenarioReport(scenarioName: String, session: Session, logs: Vector[LogInstruction], duration: FiniteDuration) extends ScenarioReport {
  val isSuccess = true

  // In case of success, logs are only shown if the scenario contains DebugLogInstruction
  val shouldShowLogs = logs.collect { case d: DebugLogInstruction ⇒ d }.nonEmpty
}

case class IgnoreScenarioReport(scenarioName: String, session: Session) extends ScenarioReport {
  val logs = emptyLogs
  val isSuccess = false
  val duration = Duration.Zero
}

case class PendingScenarioReport(scenarioName: String, session: Session) extends ScenarioReport {
  val logs = emptyLogs
  val isSuccess = false
  val duration = Duration.Zero
}

case class FailureScenarioReport(scenarioName: String, failedSteps: NonEmptyList[FailedStep], session: Session, logs: Vector[LogInstruction], duration: FiniteDuration) extends ScenarioReport {
  val isSuccess = false

  val msg =
    s"""|Scenario '$scenarioName' failed:
        |${failedSteps.map(_.messageForFailedStep).toList.mkString("\nand\n")}""".stripMargin
}

sealed abstract class Done
case object Done extends Done {
  val rightDone = Right(Done)
  val validDone = Valid(Done)
  val futureDone = Future.successful(Done)
  val taskDone = Task.now(Done)
  implicit val monoid = new Monoid[Done] {
    def empty: Done = Done
    def combine(x: Done, y: Done): Done = x
  }
}

case class FailedStep(step: Step, errors: NonEmptyList[CornichonError]) {
  val messageForFailedStep =
    s"""
       |at step:
       |${step.title}
       |
       |with error(s):
       |${errors.map(_.renderedMessage).toList.mkString("\nand\n")}
       |""".stripMargin

}

object FailedStep {
  def fromSingle(step: Step, error: CornichonError) = FailedStep(step, NonEmptyList.of(error))
}