package com.github.agourlay.cornichon.core

import java.util.Timer

import cats.data.Xor
import cats.data.Xor._

import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration.Duration
import com.github.agourlay.cornichon.core.Done._
import com.github.agourlay.cornichon.core.Engine._
import com.github.agourlay.cornichon.resolver.Resolver

import scala.util.control.NonFatal

class Engine(stepPreparers: List[StepPreparer], executionContext: ExecutionContext)(implicit timer: Timer) {

  private implicit val ec = executionContext

  def runScenario(session: Session, finallySteps: List[Step] = List.empty)(scenario: Scenario): Future[ScenarioReport] = {
    val initMargin = 1
    val titleLog = ScenarioTitleLogInstruction(s"Scenario : ${scenario.name}", initMargin)
    val initialRunState = RunState(scenario.steps, session, Vector(titleLog), initMargin + 1)
    runSteps(initialRunState).flatMap {
      case (mainState, mainRunReport) ⇒
        if (finallySteps.isEmpty)
          Future.successful(ScenarioReport.build(scenario.name, mainState, mainRunReport.toValidatedNel))
        else {
          // Reuse mainline session
          val finallyLog = InfoLogInstruction("finally steps", initMargin + 1)
          val finallyRunState = mainState.withSteps(finallySteps).withLog(finallyLog)
          runSteps(finallyRunState).map {
            case (finallyState, finallyReport) ⇒
              ScenarioReport.build(scenario.name, mainState.combine(finallyState), mainRunReport.toValidatedNel.combine(finallyReport.toValidatedNel))
          }
        }
    }
  }

  def runSteps(runState: RunState): Future[(RunState, FailedStep Xor Done)] =
    runState.remainingSteps.headOption.map { currentStep ⇒
      val preparedStep = stepPreparers.foldLeft[CornichonError Xor Step](right(currentStep)) {
        (xorStep, stepPrepared) ⇒ xorStep.flatMap(stepPrepared.run(runState.session))
      }
      preparedStep.fold(
        ce ⇒ Future.successful(Engine.exceptionToFailureStep(currentStep, runState, ce)),
        ps ⇒ ps.run(this)(runState).flatMap {
          case (newState, stepResult) ⇒
            stepResult.fold(
              failedStep ⇒ Future.successful(newState, left(failedStep)),
              _ ⇒ runSteps(newState.consumCurrentStep)
            )
        }.recover { case NonFatal(t) ⇒ exceptionToFailureStep(currentStep, runState, CornichonError.fromThrowable(t)) }
      )
    }.getOrElse(Future.successful(runState, rightDone))
}

object Engine {

  def withStepTitleResolver(resolver: Resolver, executionContext: ExecutionContext)(implicit timer: Timer) =
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