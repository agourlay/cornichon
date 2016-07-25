package com.github.agourlay.cornichon.core

import cats.data.Xor

import scala.annotation.tailrec
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.Duration

class Engine(executionContext: ExecutionContext) {

  private implicit val ec = executionContext

  def runScenario(session: Session, finallySteps: Vector[Step] = Vector.empty)(scenario: Scenario): ScenarioReport = {
    val initMargin = 1
    val titleLog = ScenarioTitleLogInstruction(s"Scenario : ${scenario.name}", initMargin)
    val (mainSession, mainRunReport) = runSteps(scenario.steps, session, Vector(titleLog), initMargin + 1)
    if (finallySteps.isEmpty)
      ScenarioReport.build(scenario.name, mainSession, mainRunReport)
    else {
      // Reuse mainline session
      val (finallySession, finallyReport) = runSteps(finallySteps, mainSession, Vector.empty, initMargin + 1)
      val combinedSession = mainSession.merge(finallySession)
      ScenarioReport.build(scenario.name, combinedSession, mainRunReport, Some(finallyReport))
    }
  }

  @tailrec
  final def runSteps(steps: Vector[Step], session: Session, accLogs: Vector[LogInstruction], depth: Int): (Session, StepsResult) =
    if (steps.isEmpty)
      (session, SuccessStepsResult(accLogs))
    else {
      val (newSession, stepResult) = steps(0).run(this, session, depth)
      stepResult match {
        case SuccessStepsResult(updatedLogs) ⇒
          val nextSteps = steps.drop(1)
          runSteps(nextSteps, newSession, accLogs ++ updatedLogs, depth)

        case f: FailureStepsResult ⇒
          (newSession, f.copy(logs = accLogs ++ f.logs))
      }
    }
}

object Engine {

  def withDuration[A](fct: ⇒ A): (A, Duration) = {
    val now = System.nanoTime
    val res = fct
    val executionTime = Duration.fromNanos(System.nanoTime - now)
    (res, executionTime)
  }

  def xorToStepReport(currentStep: Step, session: Session, res: Xor[CornichonError, Session], title: String, depth: Int, show: Boolean, duration: Option[Duration] = None) =
    res.fold(
      e ⇒ exceptionToFailureStep(currentStep, session, title, depth, e),
      newSession ⇒ {
        val runLogs = if (show) Vector(SuccessLogInstruction(title, depth, duration)) else Vector.empty
        (newSession, SuccessStepsResult(runLogs))
      }
    )

  def exceptionToFailureStep(currentStep: Step, session: Session, title: String, depth: Int, e: CornichonError): (Session, FailureStepsResult) = {
    val runLogs = errorLogs(title, e, depth)
    val failedStep = FailedStep(currentStep, e)
    (session, FailureStepsResult(failedStep, runLogs))
  }

  def errorLogs(title: String, e: Throwable, depth: Int) = {
    val failureLog = FailureLogInstruction(s"$title *** FAILED ***", depth)
    val error = CornichonError.fromThrowable(e)
    failureLog +: error.msg.split('\n').toVector.map { m ⇒
      FailureLogInstruction(m, depth)
    }
  }

}