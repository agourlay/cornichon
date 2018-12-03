package com.github.agourlay.cornichon.core

import cats.Foldable
import cats.data.{ NonEmptyList, StateT, ValidatedNel }
import cats.syntax.either._
import cats.syntax.apply._
import cats.instances.list._
import cats.data.NonEmptyList._
import cats.data.Validated._
import monix.eval.Task

import scala.concurrent.duration.Duration
import com.github.agourlay.cornichon.core.Done._
import com.github.agourlay.cornichon.core.Engine._
import com.github.agourlay.cornichon.core.core.StepResult
import com.github.agourlay.cornichon.resolver.PlaceholderResolver

import scala.collection.breakOut
import scala.util.control.NonFatal

class Engine(stepPreparers: List[StepPreparer]) {

  final def runScenario(session: Session, context: FeatureExecutionContext = FeatureExecutionContext.empty)(scenario: Scenario): Task[ScenarioReport] =
    context.isIgnored(scenario) match {
      case Some(reason) ⇒
        Task.now(IgnoreScenarioReport(scenario.name, reason, session))
      case None if context isPending scenario ⇒
        Task.now(PendingScenarioReport(scenario.name, session))
      case _ ⇒
        val stages = for {
          beforeResult ← regularStage(context.beforeSteps, beforeLog)
          mainResult ← if (beforeResult.isValid) regularStage(scenario.steps, mainLog) else noOpStage
          mainCleanupResult ← cleanupStage()
          finallyResult ← regularStage(context.finallySteps, finallyLog)
          finallyCleanupResult ← cleanupStage()
        } yield Foldable[List].fold(beforeResult :: mainResult :: mainCleanupResult :: finallyResult :: finallyCleanupResult :: Nil)

        val titleLog = ScenarioTitleLogInstruction(s"Scenario : ${scenario.name}", initMargin)
        val initialRunState = RunState(session, Vector(titleLog), initMargin + 1, Nil)
        val now = System.nanoTime
        stages.run(initialRunState).map {
          case (lastState, aggregatedResult) ⇒
            ScenarioReport.build(scenario.name, lastState, aggregatedResult, Duration.fromNanos(System.nanoTime - now))
        }
    }

  private def regularStage(steps: List[Step], stageTitle: InfoLogInstruction): StateT[Task, RunState, FailedStep ValidatedNel Done] = StateT { runState ⇒
    if (steps.isEmpty)
      Task.now((runState, validDone))
    else
      runSteps(steps, runState.appendLog(stageTitle)).map {
        case (resultState, resultReport) ⇒ (resultState, resultReport.toValidatedNel)
      }
  }

  private def cleanupStage(): StateT[Task, RunState, FailedStep ValidatedNel Done] = StateT { runState ⇒
    if (runState.cleanupSteps.isEmpty)
      Task.now((runState, validDone))
    else {
      // Cleanup steps are consumed to avoid double run
      val cleanupState = runState.resetCleanupSteps.appendLog(cleanupLog)
      runStepsDoNotShortCircuit(runState.cleanupSteps, cleanupState).map {
        case (resultState, resultReport) ⇒ (resultState, resultReport.toValidated)
      }
    }
  }

  // run steps and short-circuit on Task[Either]
  final def runSteps(remainingSteps: List[Step], initialRunState: RunState): StepResult =
    remainingSteps.foldLeft[Task[(RunState, FailedStep Either Done)]](Task.now((initialRunState, Done.asRight[FailedStep]))) {
      case (runStateF, currentStep) ⇒
        runStateF.flatMap {
          case (runState, Right(_))    ⇒ prepareAndRunStep(currentStep, runState)
          case (runState, l @ Left(_)) ⇒ Task.now((runState, l))
        }
    }

  // run steps and aggregate failed steps
  private def runStepsDoNotShortCircuit(steps: List[Step], runState: RunState): Task[(RunState, NonEmptyList[FailedStep] Either Done)] = {
    steps.foldLeft(Task.now((runState, Done: Done).asRight[NonEmptyList[(RunState, FailedStep)]])) {
      case (runStateF, currentStep) ⇒
        runStateF.flatMap { prepareAndRunStepsAccumulatingErrors(currentStep, _) }
    }.map(_.fold(
      { errors ⇒ (errors.head._1, Left(errors.map(_._2))) },
      { case (r, _) ⇒ (r, rightDone) }
    ))
  }

  private def prepareAndRunStepsAccumulatingErrors(currentStep: Step, failureOrDoneWithRunState: Either[NonEmptyList[(RunState, FailedStep)], (RunState, Done)]) = {
    val runState = failureOrDoneWithRunState.fold(_.head._1, _._1)
    // Inject RunState into Either to align on aggregation shape
    val stepResult = prepareAndRunStep(currentStep, runState).map {
      case (r, Right(_))         ⇒ Right((r, Done))
      case (r, Left(failedStep)) ⇒ Left((r, failedStep))
    }

    stepResult
      .onErrorRecover { case NonFatal(ex) ⇒ (runState, FailedStep.fromSingle(currentStep, StepExecutionError(ex))).asLeft[(RunState, Done)] }
      .map(res ⇒ (res.toValidatedNel <* failureOrDoneWithRunState.toValidated).toEither) // if current step is successful, it is propagated
  }

  private def prepareAndRunStep(currentStep: Step, runState: RunState): Task[(RunState, FailedStep Either Done)] =
    stepPreparers.foldLeft[CornichonError Either Step](currentStep.asRight) {
      (xorStep, stepPreparer) ⇒ xorStep.flatMap(stepPreparer.run(runState.session))
    }.fold(
      ce ⇒ Task.now(Engine.handleErrors(currentStep, runState, NonEmptyList.of(ce))),
      ps ⇒ runStep(runState, ps)
    )

  private def runStep(runState: RunState, ps: Step): Task[(RunState, FailedStep Either Done)] =
    Either
      .catchNonFatal(ps.run(this)(runState))
      .fold(
        e ⇒ Task.now(handleThrowable(ps, runState, e)),
        _.onErrorRecover { case NonFatal(t) ⇒ handleThrowable(ps, runState, t) }
      )
}

object Engine {

  private val initMargin = 1
  private val beforeLog = InfoLogInstruction("before steps", initMargin + 1)
  private val mainLog = InfoLogInstruction("main steps", initMargin + 1)
  private val finallyLog = InfoLogInstruction("finally steps", initMargin + 1)
  private val cleanupLog = InfoLogInstruction("cleanup steps", initMargin + 1)

  private val noOpStage: StateT[Task, RunState, FailedStep ValidatedNel Done] = StateT { s ⇒ Task.now((s, validDone)) }

  def withStepTitleResolver(resolver: PlaceholderResolver) =
    new Engine(stepPreparers = StepPreparerTitleResolver(resolver) :: Nil)

  def successLog(title: String, depth: Int, show: Boolean, duration: Duration): LogInstruction =
    if (show)
      SuccessLogInstruction(title, depth, Some(duration))
    else
      NoShowLogInstruction(title, depth, Some(duration))

  def errorsToFailureStep(currentStep: Step, depth: Int, errors: NonEmptyList[CornichonError]): (Vector[LogInstruction], FailedStep) = {
    val runLogs = errorLogs(currentStep.title, errors, depth)
    val failedStep = FailedStep(currentStep, errors)
    (runLogs, failedStep)
  }

  def handleErrors(currentStep: Step, runState: RunState, errors: NonEmptyList[CornichonError]): (RunState, FailedStep Either Done) = {
    val (runLogs, failedStep) = errorsToFailureStep(currentStep, runState.depth, errors)
    (runState.appendLogs(runLogs), failedStep.asLeft)
  }

  def handleThrowable(currentStep: Step, runState: RunState, error: Throwable): (RunState, FailedStep Either Done) = {
    val (runLogs, failedStep) = errorsToFailureStep(currentStep, runState.depth, NonEmptyList.one(CornichonError.fromThrowable(error)))
    (runState.appendLogs(runLogs), failedStep.asLeft)
  }

  def errorLogs(title: String, errors: NonEmptyList[CornichonError], depth: Int): Vector[FailureLogInstruction] = {
    val failureLogTitle = FailureLogInstruction(s"$title *** FAILED ***", depth)
    val errorLogs: Vector[FailureLogInstruction] = errors.toList.flatMap(_.renderedMessage.split('\n').map(m ⇒ FailureLogInstruction(m, depth)))(breakOut)
    failureLogTitle +: errorLogs
  }
}