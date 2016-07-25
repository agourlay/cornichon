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
    val (mainSession, mainLogs, mainRunReport) = runSteps(scenario.steps, session, Vector(titleLog), initMargin + 1)
    if (finallySteps.isEmpty)
      ScenarioReport.build(scenario.name, mainSession, mainLogs, mainRunReport)
    else {
      // Reuse mainline session
      val (finallySession, finallyLogs, finallyReport) = runSteps(finallySteps, mainSession, Vector.empty, initMargin + 1)
      val combinedSession = mainSession.merge(finallySession)
      val combinedLogs = mainLogs ++ finallyLogs
      ScenarioReport.build(scenario.name, combinedSession, combinedLogs, mainRunReport, Some(finallyReport))
    }
  }

  @tailrec
  final def runSteps(
    steps: Vector[Step],
    session: Session,
    accLogs: Vector[LogInstruction],
    depth: Int
  ): (Session, Vector[LogInstruction], Xor[FailedStep, Done]) =
    if (steps.isEmpty)
      (session, accLogs, right(Done))
    else {
      val (newSession, runLogs, stepResult) = steps(0).run(this, session, depth)
      stepResult match {
        case Right(Done) ⇒
          val nextSteps = steps.drop(1)
          runSteps(nextSteps, newSession, accLogs ++ runLogs, depth)

        case Left(failedStep) ⇒
          (newSession, accLogs ++ runLogs, left(failedStep))
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

  def xorToStepReport(
    currentStep: Step,
    session: Session,
    res: Xor[CornichonError, Session],
    title: String,
    depth: Int,
    show: Boolean,
    duration: Option[Duration] = None
  ): (Session, Vector[LogInstruction], Xor[FailedStep, Done]) =
    res.fold(
      e ⇒ exceptionToFailureStep(currentStep, session, title, depth, e),
      newSession ⇒ {
        val runLogs = if (show) Vector(SuccessLogInstruction(title, depth, duration)) else Vector.empty
        (newSession, runLogs, rightDone)
      }
    )

  def exceptionToFailureStep(currentStep: Step, session: Session, title: String, depth: Int, e: CornichonError): (Session, Vector[LogInstruction], Xor[FailedStep, Done]) = {
    val runLogs = errorLogs(title, e, depth)
    val failedStep = FailedStep(currentStep, e)
    (session, runLogs, left(failedStep))
  }

  def errorLogs(title: String, e: Throwable, depth: Int) = {
    val failureLog = FailureLogInstruction(s"$title *** FAILED ***", depth)
    val error = CornichonError.fromThrowable(e)
    failureLog +: error.msg.split('\n').toVector.map { m ⇒
      FailureLogInstruction(m, depth)
    }
  }

}