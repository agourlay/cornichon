package com.github.agourlay.cornichon.core

import cats.Foldable
import cats.data.{ NonEmptyList, ValidatedNel }
import cats.syntax.either._
import cats.syntax.cartesian._
import cats.instances.list._
import cats.data.NonEmptyList._
import cats.data.Validated._

import monix.execution.Scheduler
import monix.eval.Task

import scala.concurrent.Future
import scala.concurrent.duration.Duration
import com.github.agourlay.cornichon.core.Done._
import com.github.agourlay.cornichon.core.Engine._
import com.github.agourlay.cornichon.core.RunState._
import com.github.agourlay.cornichon.resolver.PlaceholderResolver

import scala.util.control.NonFatal

class Engine(stepPreparers: List[StepPreparer])(implicit scheduler: Scheduler) {

  def runScenario(session: Session, context: FeatureExecutionContext = FeatureExecutionContext.empty)(scenario: Scenario): Future[ScenarioReport] =
    runScenarioTask(session, context)(scenario).runAsync

  def runScenarioTask(session: Session, context: FeatureExecutionContext)(scenario: Scenario): Task[ScenarioReport] =
    if (context isIgnored scenario)
      Task.delay(IgnoreScenarioReport(scenario.name, session))
    else if (context isPending scenario)
      Task.delay(PendingScenarioReport(scenario.name, session))
    else {
      val titleLog = ScenarioTitleLogInstruction(s"Scenario : ${scenario.name}", initMargin)
      val initialRunState = RunState(session, Vector(titleLog), initMargin + 1, Nil)

      for {
        beforeResult ← runStage(context.beforeSteps, beforeLog, initialRunState)
        (beforeState, beforeReport) = beforeResult
        mainResult ← if (beforeReport.isValid) runStage(scenario.steps, mainLog, beforeState) else noOpStage
        (mainState, mainReport) = mainResult
        mainCleanupResult ← runStage(mainState.cleanupSteps, cleanupLog, mainState, shortCircuit = false)
        (mainCleanupState, mainCleanupReport) = mainCleanupResult
        finallyResult ← runStage(context.finallySteps, finallyLog, mainCleanupState.resetCleanupSteps) // 'cleanupSteps' already consumed above
        (finallyState, finallyReport) = finallyResult
        cleanupResult ← runStage(finallyState.cleanupSteps, cleanupLog, finallyState, shortCircuit = false)
        (lastState, finallyCleanupReport) = cleanupResult
      } yield {
        val aggregatedReport = Foldable[List].fold(List(beforeReport, mainReport, mainCleanupReport, finallyReport, finallyCleanupReport))
        ScenarioReport.build(scenario.name, lastState, aggregatedReport)
      }
    }

  private def runStage(
    steps: List[Step],
    stageTitle: InfoLogInstruction,
    runState: RunState,
    shortCircuit: Boolean = true): Task[(RunState, FailedStep ValidatedNel Done)] =
    if (steps.isEmpty)
      Task.delay((runState, validDone))
    else if (shortCircuit)
      runSteps(steps, runState.appendLog(stageTitle)).map {
        case (resultState, resultReport) ⇒ (resultState, resultReport.toValidatedNel)
      }
    else runStepsDontShortCircuit(steps, runState.appendLog(stageTitle)).map {
      case (resultState, resultReport) ⇒ (resultState, resultReport.toValidated)
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

  // run steps and aggregate failed steps
  private def runStepsDontShortCircuit(steps: List[Step], runState: RunState): Task[(RunState, NonEmptyList[FailedStep] Either Done)] = {
    steps.foldLeft(Task.delay((runState, Done: Done).asRight[NonEmptyList[(RunState, FailedStep)]])) {
      case (runStateF, currentStep) ⇒
        runStateF.flatMap { prepareAndRunStepsAccumulatingErrors(currentStep, _) }
    }.map(_.fold(
      { errors ⇒ (errors.head._1, Left(errors.map(_._2))) },
      { case (r, _) ⇒ (r, rightDone) }
    ))
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
      .map(res ⇒ (res.toValidatedNel <* failureOrDoneWithRunState.toValidated).toEither) // if current step is successfull, it is propagated
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
  val beforeLog = InfoLogInstruction("before steps", initMargin + 1)
  val mainLog = InfoLogInstruction("main steps", initMargin + 1)
  val finallyLog = InfoLogInstruction("finally steps", initMargin + 1)
  val cleanupLog = InfoLogInstruction("cleanup steps", initMargin + 1)

  val noOpStage = Task.delay((emptyRunState, validDone))

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