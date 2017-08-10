package com.github.agourlay.cornichon.core

import cats.data.{ EitherT, NonEmptyList }
import cats.syntax.either._
import cats.syntax.monoid._
import cats.syntax.coflatMap._
import cats.instances.future._
import cats.instances.list._

import monix.execution.Scheduler

import scala.concurrent.Future
import scala.concurrent.duration.Duration

import com.github.agourlay.cornichon.core.Done._
import com.github.agourlay.cornichon.core.Engine._
import com.github.agourlay.cornichon.resolver.PlaceholderResolver

import scala.util.control.NonFatal
import FutureEitherTHelpers._

class Engine(stepPreparers: List[StepPreparer])(implicit scheduler: Scheduler) {

  def runScenario(session: Session, finallySteps: List[Step] = Nil, featureIgnored: Boolean = false)(scenario: Scenario): Future[ScenarioReport] =
    if (featureIgnored || scenario.ignored)
      Future.successful(IgnoreScenarioReport(scenario.name, session))
    else if (scenario.pending)
      Future.successful(PendingScenarioReport(scenario.name, session))
    else {
      val titleLog = ScenarioTitleLogInstruction(s"Scenario : ${scenario.name}", initMargin)
      val initialRunState = RunState(scenario.steps, session, Vector(titleLog), initMargin + 1, Nil)
      runSteps(initialRunState).flatMap {
        case (mainState, mainRunReport) ⇒
          if (finallySteps.isEmpty && mainState.cleanupSteps.isEmpty)
            Future.successful(ScenarioReport.build(scenario.name, mainState, mainRunReport.toValidatedNel))
          else if (finallySteps.nonEmpty && mainState.cleanupSteps.isEmpty) {
            // Reuse mainline session
            val finallyLog = InfoLogInstruction("finally steps", initMargin + 1)
            val finallyRunState = mainState.withSteps(finallySteps).withLog(finallyLog)
            runSteps(finallyRunState).map {
              case (finallyState, finallyReport) ⇒
                ScenarioReport.build(scenario.name, mainState.combine(finallyState), mainRunReport.toValidatedNel.combine(finallyReport.toValidatedNel))
            }
          } else {
            // Reuse mainline session
            val finallyLog = InfoLogInstruction("cleanup steps", initMargin + 1)
            val finallyRunState = mainState.withSteps(mainState.cleanupSteps).withLog(finallyLog)
            runStepsWithoutShortCircuit(finallyRunState).map {
              case (finallyState, finallyReport) ⇒
                ScenarioReport.build(scenario.name, mainState.combine(finallyState), mainRunReport.toValidatedNel.combine(finallyReport.toValidated))
            }
          }
      }
    }

  def runStepsWithoutShortCircuit(initialRunState: RunState): Future[(RunState, NonEmptyList[FailedStep] Either Done)] = {
    import cats.syntax.cartesian._
    initialRunState.remainingSteps
      .coflatMap { case h :: t ⇒ (h, t); case _ ⇒ throw new Exception("just to silence the warnings") }
      .foldLeft(Future.successful[NonEmptyList[(RunState, FailedStep)] Either (RunState, Done)](Right((initialRunState, Done)))) {
        case (runStateF: Future[Either[NonEmptyList[(RunState, FailedStep)], (RunState, Done)]], (currentStep: Step, tail: Seq[Step])) ⇒
          runStateF.flatMap {
            failureOrDoneWithRunState ⇒
              val runState = failureOrDoneWithRunState.fold(_.head._1, _._1)
              val value = prepareAndRunStep(currentStep, tail, runState).value
              value.recover({ case NonFatal(ex) ⇒ (runState, FailedStep.fromSingle(currentStep, StepExecutionError(ex))).asLeft[(RunState, Done)] }).map {
                currentStepResult ⇒
                  (currentStepResult.toValidatedNel |@| failureOrDoneWithRunState.toValidated).map({ case ((rs1, _), (rs2, _)) ⇒ (rs1 combine rs2, Done) }).toEither
              }
          }
      }.deDup
  }

  def runSteps(initialRunState: RunState): Future[(RunState, FailedStep Either Done)] = {

    initialRunState.remainingSteps
      .coflatMap { case h :: t ⇒ (h, t); case _ ⇒ throw new Exception("just to silence the warnings") }
      .foldLeft(EitherT.pure[Future, (RunState, FailedStep), (RunState, Done)]((initialRunState, Done))) {
        case (runStateF, (currentStep, tail)) ⇒
          runStateF.flatMap({ case (runState, _) ⇒ prepareAndRunStep(currentStep, tail, runState) })
      }.runAndDeDup
  }

  private def prepareAndRunStep(currentStep: Step, tail: List[Step], runState: RunState): EitherT[Future, (RunState, FailedStep), (RunState, Done)] = {
    stepPreparers.foldLeft[CornichonError Either Step](Right(currentStep)) {
      (xorStep, stepPreparer) ⇒ xorStep.flatMap(stepPreparer.run(runState.session))
    }.fold(
      ce ⇒ Future.successful(Engine.handleErrors(currentStep, runState, NonEmptyList.of(ce))),
      ps ⇒ runStep(runState.withSteps(tail), ps)
    ).toEitherT
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

object FutureEitherTHelpers {

  implicit class ToEitherTOps[A, B, C](fe: Future[(A, B Either C)]) {
    def toEitherT(implicit ec: scala.concurrent.ExecutionContext) = EitherT(fe.map { case (rs, e) ⇒ e.bimap((rs, _), (rs, _)) })
  }

  implicit class FactorOutCommandAInEitherTOps[A, B, C](fe: EitherT[Future, (A, B), (A, C)]) {
    def runAndDeDup(implicit ec: scala.concurrent.ExecutionContext): Future[(A, Either[B, C])] = fe.value.map(_.fold(
      { case (a, b) ⇒ (a, Left(b)) },
      { case (a, c) ⇒ (a, Right(c)) }
    ))
  }

  implicit class FactorOutCommonAOps[A, B, C](fe: Future[Either[NonEmptyList[(A, B)], (A, C)]]) {
    def deDup(implicit ec: scala.concurrent.ExecutionContext): Future[(A, Either[NonEmptyList[B], C])] = fe.map(_.fold(
      { abs ⇒ (abs.head._1, Left(abs.map(_._2))) },
      { case (a, c) ⇒ (a, Right(c)) }
    ))
  }
}