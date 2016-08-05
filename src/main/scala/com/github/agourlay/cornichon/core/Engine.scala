package com.github.agourlay.cornichon.core

import cats.data.Xor
import cats.data.Xor._

import scala.annotation.tailrec
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.Duration
import com.github.agourlay.cornichon.core.Done._

class Engine(executionContext: ExecutionContext) {

  private implicit val ec = executionContext

  def runScenario(session: Session, finallySteps: Vector[Step] = Vector.empty)(scenario: Scenario): ScenarioReport = {
    val initMargin = 1
    val titleLog = ScenarioTitleLogInstruction(s"Scenario : ${scenario.name}", initMargin)
    val initialRunState = RunState(scenario.steps, session, Vector(titleLog), initMargin + 1)
    val (mainState, mainRunReport) = runSteps(initialRunState)
    if (finallySteps.isEmpty)
      ScenarioReport.build(scenario.name, mainState.session, mainState.logs, mainRunReport)
    else {
      // Reuse mainline session
      val finallyRunState = initialRunState.withSteps(finallySteps).withLog(titleLog)
      val (finallyState, finallyReport) = runSteps(finallyRunState)
      val combinedSession = mainState.session.merge(finallyState.session)
      val combinedLogs = mainState.logs ++ finallyState.logs
      ScenarioReport.build(scenario.name, combinedSession, combinedLogs, mainRunReport, Some(finallyReport))
    }
  }

  @tailrec
  final def runSteps(runState: RunState): (RunState, FailedStep Xor Done) =
    if (runState.endReached)
      (runState, rightDone)
    else {
      val (newState, stepResult) = runState.currentStep.run(this)(runState)
      stepResult match {
        case Right(Done) ⇒
          runSteps(newState.consumCurrentStep)
        case Left(failedStep) ⇒
          (newState, left(failedStep))
      }
    }
}

object Engine {

  def xorToStepReport(
    currentStep: Step,
    res: Xor[CornichonError, Session],
    title: String,
    runState: RunState,
    show: Boolean,
    duration: Option[Duration] = None
  ): (RunState, FailedStep Xor Done) =
    res.fold(
      e ⇒ exceptionToFailureStep(currentStep, runState, title, e),
      newSession ⇒ {
        val runLogs = if (show) Vector(SuccessLogInstruction(title, runState.depth, duration)) else Vector.empty
        (runState.withSession(newSession).appendLogs(runLogs), rightDone)
      }
    )

  def exceptionToFailureStep(currentStep: Step, runState: RunState, title: String, e: CornichonError): (RunState, FailedStep Xor Done) = {
    val runLogs = errorLogs(title, e, runState.depth)
    val failedStep = FailedStep(currentStep, e)
    (runState.appendLogs(runLogs), left(failedStep))
  }

  def errorLogs(title: String, e: Throwable, depth: Int) = {
    val failureLog = FailureLogInstruction(s"$title *** FAILED ***", depth)
    val error = CornichonError.fromThrowable(e)
    failureLog +: error.msg.split('\n').toVector.map { m ⇒
      FailureLogInstruction(m, depth)
    }
  }

}

case class RunState(remainingSteps: Vector[Step], session: Session, logs: Vector[LogInstruction], depth: Int) {

  lazy val endReached = remainingSteps.isEmpty

  lazy val currentStep = remainingSteps.head

  def goDeeper = copy(depth = depth + 1)

  def withSteps(steps: Vector[Step]) = copy(remainingSteps = steps)
  def consumCurrentStep = copy(remainingSteps = remainingSteps.drop(1))

  def withSession(s: Session) = copy(session = s)
  def addToSession(tuples: Seq[(String, String)]) = withSession(session.addValues(tuples))

  def withLogs(logs: Vector[LogInstruction]) = copy(logs = logs)
  def withLog(log: LogInstruction) = copy(logs = Vector(log))

  def appendLog(add: LogInstruction) = copy(logs = logs :+ add)
  def appendLogs(add: Vector[LogInstruction]) = copy(logs = logs ++ add)

  def prependLog(add: LogInstruction) = copy(logs = add +: logs)
  def prependLogs(add: Vector[LogInstruction]) = copy(logs = add ++ logs)

  def resetLogs = copy(logs = Vector.empty)

  def prependSteps(prepend: Vector[Step]) = copy(remainingSteps = prepend ++ remainingSteps)

}