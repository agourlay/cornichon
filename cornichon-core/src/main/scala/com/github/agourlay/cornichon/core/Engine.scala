package com.github.agourlay.cornichon.core

import cats.data.NonEmptyList
import cats.syntax.either._

import monix.execution.Scheduler

import scala.concurrent.Future
import scala.concurrent.duration.Duration

import com.github.agourlay.cornichon.core.Done._
import com.github.agourlay.cornichon.core.Engine._
import com.github.agourlay.cornichon.resolver.Resolver

import scala.util.control.NonFatal

class Engine(stepPreparers: List[StepPreparer])(implicit scheduler: Scheduler) {

  def runScenario(session: Session, finallySteps: List[Step] = Nil, featureIgnored: Boolean = false)(scenario: Scenario): Future[ScenarioReport] =
    if (featureIgnored || scenario.ignored)
      Future.successful(IgnoreScenarioReport(scenario.name, session, Vector.empty))
    else {
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
    if (runState.remainingSteps.isEmpty)
      Future.successful(runState, rightDone)
    else {
      val currentStep = runState.remainingSteps.head
      stepPreparers.foldLeft[CornichonError Either Step](Right(currentStep)) {
        (xorStep, stepPreparer) ⇒ xorStep.flatMap(stepPreparer.run(runState.session))
      }.fold(
        ce ⇒ Future.successful(Engine.exceptionToFailureStep(currentStep, runState, NonEmptyList.of(ce))),
        ps ⇒ runStep(runState, ps)
      )
    }

  private def runStep(runState: RunState, ps: Step) =
    Either
      .catchNonFatal(ps.run(this)(runState))
      .fold(
        e ⇒ Future.successful(exceptionToFailureStep(ps, runState, e)),
        _.flatMap {
          case (newState, stepResult) ⇒ stepResult.fold(failedStep ⇒ Future.successful(newState, Left(failedStep)), _ ⇒ runSteps(newState.consumCurrentStep))
        }.recover {
          case NonFatal(t) ⇒ exceptionToFailureStep(ps, runState, t)
        }
      )
}

object Engine {

  val initMargin = 1

  def withStepTitleResolver(resolver: Resolver)(implicit scheduler: Scheduler) =
    new Engine(stepPreparers = StepPreparerTitleResolver(resolver) :: Nil)

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

  def exceptionToFailureStep(currentStep: Step, runState: RunState, error: Throwable): (RunState, FailedStep Either Done) =
    exceptionToFailureStep(currentStep, runState, NonEmptyList.of(CornichonError.fromThrowable(error)))

  def errorLogs(title: String, errors: NonEmptyList[CornichonError], depth: Int) = {
    val failureLog = FailureLogInstruction(s"$title *** FAILED ***", depth)
    val logs = failureLog +: errors.toList.flatMap(_.renderedMessage.split('\n').toList.map { m ⇒
      FailureLogInstruction(m, depth)
    })
    logs.toVector
  }
}