package com.github.agourlay.cornichon.core

import cats.data.Xor
import cats.data.Xor._

import scala.concurrent.{ Await, ExecutionContext, Future }
import scala.concurrent.duration.Duration
import scala.concurrent.duration._
import com.github.agourlay.cornichon.core.Done._
import com.github.agourlay.cornichon.core.Engine._
import com.github.agourlay.cornichon.resolver.Resolver

import scala.util.control.NonFatal

class Engine(stepPreparers: List[StepPreparer], executionContext: ExecutionContext) {

  private implicit val ec = executionContext

  //TODO define max duration scenario
  def runScenario(session: Session, finallySteps: Vector[Step] = Vector.empty)(scenario: Scenario): ScenarioReport = {
    val initMargin = 1
    val titleLog = ScenarioTitleLogInstruction(s"Scenario : ${scenario.name}", initMargin)
    val initialRunState = RunState(scenario.steps, session, Vector(titleLog), initMargin + 1)
    val (mainState, mainRunReport) = Await.result(runSteps(initialRunState), 30.seconds)
    if (finallySteps.isEmpty)
      ScenarioReport.build(scenario.name, mainState.session, mainState.logs, mainRunReport)
    else {
      // Reuse mainline session
      val finallyLog = InfoLogInstruction("finally steps", initMargin + 1)
      val finallyRunState = mainState.withSteps(finallySteps).withLog(finallyLog)
      val (finallyState, finallyReport) = Await.result(runSteps(finallyRunState), 30.seconds)
      val combinedSession = mainState.session.merge(finallyState.session)
      val combinedLogs = mainState.logs ++ finallyState.logs
      ScenarioReport.build(scenario.name, combinedSession, combinedLogs, mainRunReport, Some(finallyReport))
    }
  }

  def runSteps(runState: RunState): Future[(RunState, FailedStep Xor Done)] =
    if (runState.endReached) Future.successful(runState, rightDone)
    else {
      val currentStep = runState.currentStep
      val currentSession = runState.session
      val preparedStep = stepPreparers.foldLeft[CornichonError Xor Step](right(currentStep)) {
        (xorStep, stepPrepared) ⇒ xorStep.flatMap(stepPrepared.run(currentSession))
      }
      preparedStep.fold(
        ce ⇒
          Future.successful(Engine.exceptionToFailureStep(currentStep, runState, ce)),
        ps ⇒
          ps.run(this)(runState).flatMap {
            case (newState, stepResult) ⇒
              stepResult match {
                case Right(Done) ⇒
                  runSteps(newState.consumCurrentStep)
                case Left(failedStep) ⇒
                  Future.successful(newState, left(failedStep))
              }
          }.recover {
            case NonFatal(t) ⇒ exceptionToFailureStep(currentStep, runState, CornichonError.fromThrowable(t))
          }
      )
    }
}

object Engine {

  def withStepTitleResolver(resolver: Resolver, executionContext: ExecutionContext) =
    new Engine(
      stepPreparers = StepPreparerTitleResolver(resolver) :: Nil,
      executionContext = executionContext
    )

  def xorToStepReport(
    currentStep: Step,
    res: Xor[CornichonError, Session],
    runState: RunState,
    show: Boolean,
    duration: Option[Duration] = None
  ): (RunState, FailedStep Xor Done) =
    res.fold(
      e ⇒ exceptionToFailureStep(currentStep, runState, e),
      newSession ⇒ {
        val runLogs = if (show) Vector(SuccessLogInstruction(currentStep.title, runState.depth, duration)) else Vector.empty
        (runState.withSession(newSession).appendLogs(runLogs), rightDone)
      }
    )

  def exceptionToFailureStep(currentStep: Step, runState: RunState, e: CornichonError): (RunState, FailedStep Xor Done) = {
    val runLogs = errorLogs(currentStep.title, e, runState.depth)
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