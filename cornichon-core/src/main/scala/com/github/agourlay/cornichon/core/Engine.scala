package com.github.agourlay.cornichon.core

import cats.data.{ EitherT, NonEmptyList }
import cats.syntax.either._
import cats.syntax.monoid._
import cats.syntax.cartesian._
import cats.syntax.coflatMap._

import cats.instances.list._
import cats.instances.tuple._
import monix.execution.Scheduler
import monix.eval.Task
import monix.cats.monixToCatsMonad

import scala.concurrent.Future
import scala.concurrent.duration.Duration
import com.github.agourlay.cornichon.core.Done._
import com.github.agourlay.cornichon.core.Engine._
import com.github.agourlay.cornichon.resolver.PlaceholderResolver

import scala.util.control.NonFatal
import FutureEitherTHelpers._

class Engine(stepPreparers: List[StepPreparer])(implicit scheduler: Scheduler) {

  def runScenario(session: Session, finallySteps: List[Step] = Nil, featureIgnored: Boolean = false, focusedScenarios: Set[String] = Set.empty)(scenario: Scenario): Future[ScenarioReport] =
    runScenarioTask(session, finallySteps, featureIgnored, focusedScenarios)(scenario).runAsync

  def runScenarioTask(session: Session, finallySteps: List[Step] = Nil, featureIgnored: Boolean = false, focusedScenarios: Set[String] = Set.empty)(scenario: Scenario): Task[ScenarioReport] =
    if (featureIgnored || scenario.ignored || (focusedScenarios.nonEmpty && !focusedScenarios.contains(scenario.name)))
      Task.delay(IgnoreScenarioReport(scenario.name, session))
    else if (scenario.pending)
      Task.delay(PendingScenarioReport(scenario.name, session))
    else {
      val titleLog = ScenarioTitleLogInstruction(s"Scenario : ${scenario.name}", initMargin)
      val initialRunState = RunState(scenario.steps, session, Vector(titleLog), initMargin + 1, Nil)
      runSteps(initialRunState).flatMap {
        case (mainState, mainRunReport) ⇒
          val stateAndReporAfterEndSteps = {
            if (finallySteps.isEmpty && mainState.cleanupSteps.isEmpty)
              Task.delay((mainState, validDone))
            else if (finallySteps.nonEmpty && mainState.cleanupSteps.isEmpty) {
              // Reuse mainline session
              val finallyLog = InfoLogInstruction("finally steps", initMargin + 1)
              val finallyRunState = mainState.withSteps(finallySteps).withLog(finallyLog)
              runSteps(finallyRunState).map {
                case (finallyState, finallyReport) ⇒ (mainState.combine(finallyState), finallyReport.toValidatedNel)
              }
            } else {
              // Reuse mainline session
              val finallyLog = InfoLogInstruction("cleanup steps", initMargin + 1)
              val finallyRunState = mainState.withSteps(mainState.cleanupSteps).withLog(finallyLog)
              runStepsDontShortCircuit(finallyRunState).map {
                case (finallyState, finallyReport) ⇒ (mainState.combine(finallyState), finallyReport.toValidated)
              }
            }
          }
          stateAndReporAfterEndSteps.map {
            case (state, report) ⇒
              ScenarioReport.build(scenario.name, state, mainRunReport.toValidatedNel.combine(report))
          }
      }
    }

  private def runStepsDontShortCircuit(initialRunState: RunState): Task[(RunState, NonEmptyList[FailedStep] Either Done)] = {
    initialRunState.remainingSteps
      .coflatMap { case h :: t ⇒ (h, t); case _ ⇒ throw new Exception("just to silence the warnings") }
      .foldLeft(Task.delay((initialRunState, Done: Done).asRight[NonEmptyList[(RunState, FailedStep)]])) {
        case (runStateF, (currentStep, tail)) ⇒
          runStateF.flatMap { prepareAndRunStepsAccumulatingErrors(currentStep, tail, _) }
      }.deDup
  }

  def runSteps(initialRunState: RunState): Task[(RunState, FailedStep Either Done)] =
    initialRunState.remainingSteps
      .coflatMap { case h :: t ⇒ (h, t); case _ ⇒ throw new Exception("just to silence the warnings") }
      .foldLeft(EitherT.pure[Task, (RunState, FailedStep), (RunState, Done)]((initialRunState, Done))) {
        case (runStateF, (currentStep, tail)) ⇒
          runStateF.flatMap { case (runState, _) ⇒ prepareAndRunStep(currentStep, tail, runState) }
      }.runAndDeDup

  private def prepareAndRunStepsAccumulatingErrors(currentStep: Step, tail: List[Step], failureOrDoneWithRunState: Either[NonEmptyList[(RunState, FailedStep)], (RunState, Done)]) = {
    val runState = failureOrDoneWithRunState.fold(_.head._1, _._1)
    val stepResult = prepareAndRunStep(currentStep, tail, runState).value
    stepResult
      .onErrorRecover(fromExceptionsWithFailedSteps(runState, currentStep))
      .map {
        currentStepResult ⇒
          (currentStepResult.toValidatedNel |@| failureOrDoneWithRunState.toValidated).map {
            case (r1, r2) ⇒ r1 combine r2
          }.toEither
      }
  }

  private def fromExceptionsWithFailedSteps(runState: RunState, step: Step): PartialFunction[Throwable, (RunState, FailedStep) Either (RunState, Done)] = {
    case NonFatal(ex) ⇒
      (runState, FailedStep.fromSingle(step, StepExecutionError(ex))).asLeft[(RunState, Done)]
  }

  private def prepareAndRunStep(currentStep: Step, tail: List[Step], runState: RunState): EitherT[Task, (RunState, FailedStep), (RunState, Done)] = {
    stepPreparers.foldLeft[CornichonError Either Step](Right(currentStep)) {
      (xorStep, stepPreparer) ⇒ xorStep.flatMap(stepPreparer.run(runState.session))
    }.fold(
      ce ⇒ Task.delay(Engine.handleErrors(currentStep, runState, NonEmptyList.of(ce))),
      ps ⇒ runStep(runState.withSteps(tail), ps)
    ).toEitherT
  }

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

object FutureEitherTHelpers {

  implicit class ToEitherTOps[A, B, C](fe: Task[(A, B Either C)]) {
    def toEitherT = EitherT(fe.map { case (rs, e) ⇒ e.bimap((rs, _), (rs, _)) })
  }

  implicit class FactorOutCommandAInEitherTOps[A, B, C](fe: EitherT[Task, (A, B), (A, C)]) {
    def runAndDeDup: Task[(A, Either[B, C])] = fe.value.map(_.fold(
      { case (a, b) ⇒ (a, Left(b)) },
      { case (a, c) ⇒ (a, Right(c)) }
    ))
  }

  implicit class FactorOutCommonAOps[A, B, C](fe: Task[Either[NonEmptyList[(A, B)], (A, C)]]) {
    def deDup: Task[(A, Either[NonEmptyList[B], C])] = fe.map(_.fold(
      { abs ⇒ (abs.head._1, Left(abs.map(_._2))) },
      { case (a, c) ⇒ (a, Right(c)) }
    ))
  }
}