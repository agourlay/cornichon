package com.github.agourlay.cornichon.core

import cats.data.Validated.Valid
import cats.data.{ NonEmptyList, ValidatedNel }
import cats.kernel.Monoid
import monix.eval.Task

import scala.concurrent.Future
import scala.concurrent.duration.{ Duration, FiniteDuration }

sealed trait ScenarioReport {
  def isSuccess: Boolean
  def scenarioName: String
  def session: Session
  def logs: List[LogInstruction]
  def duration: FiniteDuration
}

object ScenarioReport {
  def build(scenarioName: String, runState: RunState, result: ValidatedNel[FailedStep, Done], duration: FiniteDuration): ScenarioReport =
    result.fold(
      failedSteps ⇒ FailureScenarioReport(scenarioName, failedSteps, runState.session, runState.logStack, duration, runState.randomContext.initialSeed),
      _ ⇒ SuccessScenarioReport(scenarioName, runState.session, runState.logStack, duration)
    )
}

case class SuccessScenarioReport(scenarioName: String, session: Session, logStack: List[LogInstruction], duration: FiniteDuration) extends ScenarioReport {
  val isSuccess = true

  // keeping it lazy to avoid the reverse in case of no rendering
  lazy val logs = logStack.reverse
  // In case of success, logs are only shown if the scenario contains DebugLogInstruction
  lazy val shouldShowLogs: Boolean = logStack.exists(_.isInstanceOf[DebugLogInstruction])
}

case class IgnoreScenarioReport(scenarioName: String, reason: String, session: Session) extends ScenarioReport {
  val logs = Nil
  val isSuccess = false
  val duration = Duration.Zero
}

case class PendingScenarioReport(scenarioName: String, session: Session) extends ScenarioReport {
  val logs = Nil
  val isSuccess = false
  val duration = Duration.Zero
}

case class FailureScenarioReport(scenarioName: String, failedSteps: NonEmptyList[FailedStep], session: Session, logStack: List[LogInstruction], duration: FiniteDuration, seed: Long) extends ScenarioReport {
  val isSuccess = false

  val msg =
    s"""|Scenario '$scenarioName' failed:
        |${failedSteps.map(_.messageForFailedStep).toList.mkString("\nand\n")}
        |seed for the run was '$seed'
        |""".stripMargin

  lazy val logs = logStack.reverse
  lazy val renderedColoredLogs = LogInstruction.renderLogs(logs)
  lazy val renderedLogs = LogInstruction.renderLogs(logs, colorized = false)
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
  lazy val messageForFailedStep =
    s"""
       |at step:
       |${step.title}
       |
       |with error(s):
       |${errors.map(_.renderedMessage).toList.mkString("\nand\n")}
       |""".stripMargin

}

object FailedStep {
  def fromSingle(step: Step, error: CornichonError) = FailedStep(step, NonEmptyList.one(error))
}