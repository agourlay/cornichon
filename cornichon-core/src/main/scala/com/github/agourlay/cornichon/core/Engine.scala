package com.github.agourlay.cornichon.core

import cats.data.NonEmptyList
import cats.syntax.either._
import cats.syntax.monoid._

import monix.execution.Scheduler

import scala.concurrent.Future
import scala.concurrent.duration.Duration

import com.github.agourlay.cornichon.core.Done._
import com.github.agourlay.cornichon.core.Engine._
import com.github.agourlay.cornichon.resolver.PlaceholderResolver

import scala.util.control.NonFatal

class Engine(stepPreparers: List[StepPreparer])(implicit scheduler: Scheduler) {

  def runScenario(session: Session, finallySteps: List[Step] = Nil, featureIgnored: Boolean = false)(scenario: Scenario): Future[ScenarioReport] =
    if (featureIgnored || scenario.ignored)
      Future.successful(IgnoreScenarioReport(scenario.name, session))
    else if (scenario.pending)
      Future.successful(PendingScenarioReport(scenario.name, session))
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
    runState.remainingSteps match {
      case Nil ⇒ Future.successful((runState, rightDone))
      case currentStep :: tail ⇒
        stepPreparers.foldLeft[CornichonError Either Step](Right(currentStep)) {
          (xorStep, stepPreparer) ⇒ xorStep.flatMap(stepPreparer.run(runState.session))
        }.fold(
          ce ⇒ Future.successful(Engine.handleErrors(currentStep, runState, NonEmptyList.of(ce))),
          ps ⇒ runStep(runState, ps).flatMap {
            case (newState, stepResult) ⇒ stepResult.fold(
              failedStep ⇒ Future.successful((newState, Left(failedStep))),
              _ ⇒ runSteps(newState.withSteps(tail))
            )
          }
        )
    }

  private def runStep(runState: RunState, ps: Step): Future[(RunState, FailedStep Either Done)] =
    Either
      .catchNonFatal(ps.run(this)(runState))
      .fold(
        e ⇒ Future.successful(handleThrowable(ps, runState, e)),
        _.recover { case NonFatal(t) ⇒ handleThrowable(ps, runState, t) }
      )
}

object Engine {

  val initMargin = 1

  def withStepTitleResolver(resolver: PlaceholderResolver)(implicit scheduler: Scheduler) =
    new Engine(stepPreparers = StepPreparerTitleResolver(resolver) :: Nil)

  def successLog(title: String, depth: Int, show: Boolean, duration: Duration) =
    if (show)
      Some(SuccessLogInstruction(title, depth, Some(duration)))
    else
      None

  def errorsToFailureStep(currentStep: Step, depth: Int, errors: NonEmptyList[CornichonError]): (Vector[LogInstruction], FailedStep) = {
    val runLogs = errorLogs(currentStep.title, errors, depth)
    val failedStep = FailedStep(currentStep, errors)
    (runLogs, failedStep)
  }

  def handleErrors(currentStep: Step, runState: RunState, errors: NonEmptyList[CornichonError]): (RunState, FailedStep Either Done) = {
    val (runLogs, failedStep) = errorsToFailureStep(currentStep, runState.depth, errors)
    (runState.appendLogs(runLogs), Left(failedStep))
  }

  def handleThrowable(currentStep: Step, runState: RunState, error: Throwable): (RunState, FailedStep Either Done) = {
    val (runLogs, failedStep) = errorsToFailureStep(currentStep, runState.depth, NonEmptyList.of(CornichonError.fromThrowable(error)))
    (runState.appendLogs(runLogs), Left(failedStep))
  }

  def errorLogs(title: String, errors: NonEmptyList[CornichonError], depth: Int) = {
    val failureLog = FailureLogInstruction(s"$title *** FAILED ***", depth)
    val logs = failureLog +: errors.toList.flatMap(_.renderedMessage.split('\n').map { m ⇒
      FailureLogInstruction(m, depth)
    })
    logs.toVector
  }
}