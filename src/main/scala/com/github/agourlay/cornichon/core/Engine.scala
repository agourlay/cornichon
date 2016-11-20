package com.github.agourlay.cornichon.core

import java.util.Timer

import cats.data.NonEmptyList
import cats.syntax.either._

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

  def runSteps(runState: RunState): Future[(RunState, FailedStep Either Done)] =
    runState.remainingSteps.headOption.map { currentStep ⇒
      val preparedStep = stepPreparers.foldLeft[CornichonError Either Step](Right(currentStep)) {
        (xorStep, stepPrepared) ⇒ xorStep.flatMap(stepPrepared.run(runState.session))
      }
      preparedStep.fold(
        ce ⇒ Future.successful(Engine.exceptionToFailureStep(currentStep, runState, NonEmptyList.of(ce))),
        ps ⇒ ps.run(this)(runState).flatMap {
          case (newState, stepResult) ⇒
            stepResult.fold(
              failedStep ⇒ Future.successful(newState, Left(failedStep)),
              _ ⇒ runSteps(newState.consumCurrentStep)
            )
        }.recover { case NonFatal(t) ⇒ exceptionToFailureStep(currentStep, runState, NonEmptyList.of(CornichonError.fromThrowable(t))) }
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
    res: Either[NonEmptyList[CornichonError], Session],
    runState: RunState,
    show: Boolean,
    duration: Option[Duration] = None
  ): (RunState, FailedStep Either Done) =
    res.fold(
      e ⇒ exceptionToFailureStep(currentStep, runState, e),
      newSession ⇒ {
        val runLogs = if (show) Vector(SuccessLogInstruction(currentStep.title, runState.depth, duration)) else Vector.empty
        (runState.withSession(newSession).appendLogs(runLogs), rightDone)
      }
    )

  def exceptionToFailureStep(currentStep: Step, runState: RunState, errors: NonEmptyList[CornichonError]): (RunState, FailedStep Either Done) = {
    val runLogs = errorLogs(currentStep.title, errors, runState.depth)
    val failedStep = FailedStep(currentStep, errors)
    (runState.appendLogs(runLogs), Left(failedStep))
  }

  def errorLogs(title: String, errors: NonEmptyList[CornichonError], depth: Int) = {
    val failureLog = FailureLogInstruction(s"$title *** FAILED ***", depth)
    val logs = failureLog +: errors.toList.flatMap(_.renderedMessage.split('\n').toList.map { m ⇒
      FailureLogInstruction(m, depth)
    })
    logs.toVector
  }
}