package com.github.agourlay.cornichon.core

import cats.data.Xor

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.Duration

class Engine(executionContext: ExecutionContext) {

  private implicit val ec = executionContext

  def runScenario(session: Session, finallySteps: Seq[Step] = Seq.empty)(scenario: Scenario): ScenarioReport = {
    val initMargin = 1
    val titleLog = DefaultLogInstruction(s"Scenario : ${scenario.name}", initMargin)
    val mainRunReport = runSteps(scenario.steps, session, Vector(titleLog), initMargin + 1)
    if (finallySteps.isEmpty)
      ScenarioReport(scenario.name, mainRunReport)
    else {
      // Reuse mainline session
      val finallyReport = runSteps(finallySteps.toVector, mainRunReport.session, Vector.empty, initMargin + 1)
      val mergedReport = mainRunReport.merge(finallyReport)
      ScenarioReport(scenario.name, mergedReport)
    }
  }

  def runSteps(steps: Vector[Step], session: Session, accLogs: Vector[LogInstruction], depth: Int): StepsReport =
    steps.headOption.fold[StepsReport](SuccessRunSteps(session, accLogs)) { step ⇒
      step.run(this, session, depth) match {
        case SuccessRunSteps(newSession, updatedLogs) ⇒
          val nextSteps = steps.drop(1)
          runSteps(nextSteps, newSession, accLogs ++ updatedLogs, depth)

        case f @ FailedRunSteps(_, _, failedRunLogs, _) ⇒
          val notExecutedSteps = steps.drop(1)
          f.copy(logs = accLogs ++ failedRunLogs ++ logNonExecutedStep(notExecutedSteps, depth))
      }
    }

  def XorToStepReport(currentStep: Step, session: Session, res: Xor[CornichonError, Session], title: String, depth: Int, show: Boolean, duration: Option[Duration] = None) =
    res match {
      case Xor.Left(e) ⇒
        val runLogs = errorLogs(title, e, depth)
        FailedRunSteps(currentStep, e, runLogs, session)

      case Xor.Right(newSession) ⇒
        val runLogs = if (show) Vector(SuccessLogInstruction(title, depth, duration)) else Vector.empty
        SuccessRunSteps(newSession, runLogs)
    }

  def logNonExecutedStep(steps: Seq[Step], depth: Int) = {
    //TODO dig recursively within wrapper steps nested steps
    //steps.map { step ⇒ InfoLogInstruction(step.title, depth)}
    Vector.empty[LogInstruction]
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