package com.github.agourlay.cornichon.core

import cats.data.NonEmptyList
import cats.syntax.either._
import cats.syntax.cartesian._

import monix.execution.Scheduler
import monix.eval.Task

import scala.concurrent.Future
import scala.concurrent.duration.Duration
import com.github.agourlay.cornichon.core.Done._
import com.github.agourlay.cornichon.core.Engine._
import com.github.agourlay.cornichon.resolver.PlaceholderResolver

import scala.util.control.NonFatal

class Engine(stepPreparers: List[StepPreparer])(implicit scheduler: Scheduler) {

  def runScenario(session: Session, context: ScenarioExecutionContext = ScenarioExecutionContext.empty)(scenario: Scenario): Future[ScenarioReport] =
    runScenarioTask(session, context)(scenario).runAsync

  def runScenarioTask(session: Session, context: ScenarioExecutionContext)(scenario: Scenario): Task[ScenarioReport] =
    if (context isIgnored scenario)
      Task.delay(IgnoreScenarioReport(scenario.name, session))
    else if (context isPending scenario)
      Task.delay(PendingScenarioReport(scenario.name, session))
    else {
      val titleLog = ScenarioTitleLogInstruction(s"Scenario : ${scenario.name}", initMargin)
      val initialRunState = RunState(session, Vector(titleLog), initMargin + 1, Nil)
      runSteps(scenario.steps, initialRunState).flatMap {
        case (mainState, mainReport) ⇒
          val stateAndReporAfterEndSteps = {
            if (context.finallySteps.isEmpty && mainState.cleanupSteps.isEmpty)
              Task.delay((mainState, validDone))
            else if (context.finallySteps.nonEmpty && mainState.cleanupSteps.isEmpty) {
              // Reuse mainline session
              val finallyRunState = mainState.appendLog(finallyLog)
              runSteps(context.finallySteps, finallyRunState).map {
                case (finallyState, finallyReport) ⇒ (finallyState, finallyReport.toValidatedNel)
              }
            } else {
              // Reuse mainline session
              val finallyRunState = mainState.appendLog(cleanupLog)
              runStepsDontShortCircuit(mainState.cleanupSteps, finallyRunState).map {
                case (finallyState, finallyReport) ⇒ (finallyState, finallyReport.toValidated)
              }
            }
          }
          stateAndReporAfterEndSteps.map {
            case (state, report) ⇒
              ScenarioReport.build(scenario.name, state, mainReport.toValidatedNel.combine(report))
          }
      }
    }

  // run steps and aggregate failed steps
  private def runStepsDontShortCircuit(remainingSteps: List[Step], initialRunState: RunState): Task[(RunState, NonEmptyList[FailedStep] Either Done)] = {
    remainingSteps.foldLeft(Task.delay((initialRunState, Done: Done).asRight[NonEmptyList[(RunState, FailedStep)]])) {
      case (runStateF, currentStep) ⇒
        runStateF.flatMap { prepareAndRunStepsAccumulatingErrors(currentStep, _) }
    }.map(_.fold(
      { errors ⇒ (errors.head._1, Left(errors.map(_._2))) },
      { case (r, _) ⇒ (r, rightDone) }
    ))
  }

  // run steps and short-circuit on Task[Either]
  def runSteps(remainingSteps: List[Step], initialRunState: RunState): Task[(RunState, FailedStep Either Done)] =
    remainingSteps.foldLeft[Task[(RunState, FailedStep Either Done)]](Task.delay((initialRunState, Done.asRight[FailedStep]))) {
      case (runStateF, currentStep) ⇒
        runStateF.flatMap {
          case (runState, Right(_))    ⇒ prepareAndRunStep(currentStep, runState)
          case (runState, l @ Left(_)) ⇒ Task.delay((runState, l))
        }
    }

  private def prepareAndRunStepsAccumulatingErrors(currentStep: Step, failureOrDoneWithRunState: Either[NonEmptyList[(RunState, FailedStep)], (RunState, Done)]) = {
    val runState = failureOrDoneWithRunState.fold(_.head._1, _._1)
    // Inject RunState into Either to align on aggreation shape
    val stepResult = prepareAndRunStep(currentStep, runState).map {
      case (r, Right(_))         ⇒ Right((r, Done))
      case (r, Left(failedStep)) ⇒ Left((r, failedStep))
    }

    stepResult
      .onErrorRecover { case NonFatal(ex) ⇒ (runState, FailedStep.fromSingle(currentStep, StepExecutionError(ex))).asLeft[(RunState, Done)] }
      .map {
        currentStepResult ⇒
          (currentStepResult.toValidatedNel |@| failureOrDoneWithRunState.toValidated).map {
            case ((r1, _), (r2, _)) ⇒ (r1.mergeNested(r2), Done)
          }.toEither
      }
  }

  private def prepareAndRunStep(currentStep: Step, runState: RunState): Task[(RunState, FailedStep Either Done)] =
    stepPreparers.foldLeft[CornichonError Either Step](currentStep.asRight) {
      (xorStep, stepPreparer) ⇒ xorStep.flatMap(stepPreparer.run(runState.session))
    }.fold(
      ce ⇒ Task.delay(Engine.handleErrors(currentStep, runState, NonEmptyList.of(ce))),
      ps ⇒ runStep(runState, ps)
    )

  private def runStep(runState: RunState, ps: Step): Task[(RunState, FailedStep Either Done)] =
    Either
      .catchNonFatal(ps.run(this)(runState))
      .fold(
        e ⇒ Task.delay(handleThrowable(ps, runState, e)),
        _.onErrorRecover { case NonFatal(t) ⇒ handleThrowable(ps, runState, t) }
      )
}

object Engine {

  val initMargin = 1
  val finallyLog = InfoLogInstruction("finally steps", initMargin + 1)
  val cleanupLog = InfoLogInstruction("cleanup steps", initMargin + 1)

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