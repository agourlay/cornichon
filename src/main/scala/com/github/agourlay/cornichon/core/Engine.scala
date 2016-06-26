package com.github.agourlay.cornichon.core

import cats.data.Xor

import scala.annotation.tailrec
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.Duration

class Engine(executionContext: ExecutionContext) {

  private implicit val ec = executionContext

  def runScenario(session: Session, finallySteps: Seq[Step] = Seq.empty)(scenario: Scenario): ScenarioReport = {
    val initMargin = 1
    val titleLog = ScenarioTitleLogInstruction(s"Scenario : ${scenario.name}", initMargin)
    val mainRunReport = runSteps(scenario.steps, session, Vector(titleLog), initMargin + 1)
    if (finallySteps.isEmpty)
      ScenarioReport.build(scenario.name, mainRunReport)
    else {
      // Reuse mainline session
      val finallyReport = runSteps(finallySteps.toVector, mainRunReport.session, Vector.empty, initMargin + 1)
      ScenarioReport.build(scenario.name, mainRunReport, Some(finallyReport))
    }
  }

  @tailrec
  final def runSteps(steps: Vector[Step], session: Session, accLogs: Vector[LogInstruction], depth: Int): StepsResult =
    if (steps.isEmpty)
      SuccessStepsResult(session, accLogs)
    else
      steps(0).run(this, session, depth) match {
        case SuccessStepsResult(newSession, updatedLogs) ⇒
          val nextSteps = steps.drop(1)
          runSteps(nextSteps, newSession, accLogs ++ updatedLogs, depth)

        case f: FailureStepsResult ⇒
          f.copy(logs = accLogs ++ f.logs)
      }

  def XorToStepReport(currentStep: Step, session: Session, res: Xor[CornichonError, Session], title: String, depth: Int, show: Boolean, duration: Option[Duration] = None) =
    res.fold(
      e ⇒ exceptionToFailureStep(currentStep, session, title, depth, e),
      newSession ⇒ {
        val runLogs = if (show) Vector(SuccessLogInstruction(title, depth, duration)) else Vector.empty
        SuccessStepsResult(newSession, runLogs)
      }
    )

  def exceptionToFailureStep(currentStep: Step, session: Session, title: String, depth: Int, e: CornichonError): FailureStepsResult = {
    val runLogs = errorLogs(title, e, depth)
    val failedStep = FailedStep(currentStep, e)
    FailureStepsResult(failedStep, session, runLogs)
  }

  def errorLogs(title: String, e: Throwable, depth: Int) = {
    val failureLog = FailureLogInstruction(s"$title *** FAILED ***", depth)
    val error = CornichonError.fromThrowable(e)
    failureLog +: error.msg.split('\n').toVector.map { m ⇒
      FailureLogInstruction(m, depth)
    }
  }

  def withDuration[A](fct: ⇒ A): (A, Duration) = {
    val now = System.nanoTime
    val res = fct
    val executionTime = Duration.fromNanos(System.nanoTime - now)
    (res, executionTime)
  }
}