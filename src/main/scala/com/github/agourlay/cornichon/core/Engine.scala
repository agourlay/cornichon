package com.github.agourlay.cornichon.core

import cats.data.Xor
import cats.data.Xor.{ left, right }

import scala.Console._
import scala.annotation.tailrec
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ Await, Future }
import scala.concurrent.duration.Duration
import scala.util._

class Engine {

  def runScenario(scenario: Scenario)(session: Session): ScenarioReport = {
    val initLogs = Seq(DefaultLogInstruction(s"Scenario : ${scenario.name}"))
    runSteps(scenario.steps, session, initLogs) match {
      case s @ SuccessRunSteps(_, _)   ⇒ SuccessScenarioReport(scenario, s)
      case f @ FailedRunSteps(_, _, _) ⇒ FailedScenarioReport(scenario, f)
    }
  }

  private[cornichon] def runSteps(steps: Seq[Step], session: Session, logs: Seq[LogInstruction]): StepsReport =
    steps.headOption.fold[StepsReport](SuccessRunSteps(session, logs)) {
      case DebugStep(message) ⇒
        runSteps(steps.tail, session, logs :+ ColoredLogInstruction(message(session), CYAN))

      case e @ EventuallyStart(conf) ⇒
        val updatedLogs = logs :+ DefaultLogInstruction(s"   ${e.title}")
        val eventuallySteps = findEnclosedSteps(e, steps.tail)

        if (eventuallySteps.isEmpty) {
          val updatedLogs = logs ++ logStepErrorResult(s"   ${e.title}", MalformedEventuallyBloc, RED) ++ logNonExecutedStep(steps.tail)
          buildFailedRunSteps(steps, e, MalformedConcurrentBloc, updatedLogs)
        } else {
          val now = System.nanoTime
          val res = retryEventuallySteps(eventuallySteps, session, updatedLogs, conf)
          val executionTime = Duration.fromNanos(System.nanoTime - now)
          val nextSteps = steps.tail.drop(eventuallySteps.size)
          res match {
            case s @ SuccessRunSteps(sSession, sLogs) ⇒
              val fullLogs = sLogs :+ DefaultLogInstruction(s"   Eventually bloc succeeded in ${executionTime.toMillis} millis.")
              runSteps(nextSteps, sSession, fullLogs)
            case f @ FailedRunSteps(_, _, eLogs) ⇒
              val fullLogs = (eLogs :+ ColoredLogInstruction(s"   Eventually bloc did not complete in time. (${executionTime.toMillis} millis) ", RED)) ++ logNonExecutedStep(nextSteps)
              f.copy(logs = fullLogs)
          }
        }

      case EventuallyStop(conf) ⇒
        runSteps(steps.tail, session, logs)

      case ConcurrentStop(factor) ⇒
        runSteps(steps.tail, session, logs)

      case c @ ConcurrentStart(factor, maxTime) ⇒
        val updatedLogs = logs :+ DefaultLogInstruction(s"   ${c.title}")
        val concurrentSteps = findEnclosedSteps(c, steps.tail)

        if (concurrentSteps.isEmpty) {
          val innerLogs = logs ++ logStepErrorResult(s"   ${c.title}", MalformedConcurrentBloc, RED) ++ logNonExecutedStep(steps.tail)
          buildFailedRunSteps(steps, c, MalformedConcurrentBloc, innerLogs)
        } else {
          val now = System.nanoTime
          val results = Await.result(
            Future.traverse(List.fill(factor)(concurrentSteps)) { steps ⇒
              Future { runSteps(steps, session, updatedLogs) }
            }, maxTime
          )
          val executionTime = Duration.fromNanos(System.nanoTime - now)
          val (successStepsRun, failedStepsRun) =
            (
              results.collect { case s @ SuccessRunSteps(_, _) ⇒ s },
              results.collect { case f @ FailedRunSteps(_, _, _) ⇒ f }
            )

          val nextSteps = steps.tail.drop(concurrentSteps.size)
          if (failedStepsRun.isEmpty) {
            val updatedSession = successStepsRun.head.session
            val updatedLogs = successStepsRun.head.logs :+ DefaultLogInstruction(s"   Concurrently bloc with factor '$factor' succeeded in ${executionTime.toMillis} millis.")
            runSteps(nextSteps, updatedSession, updatedLogs)
          } else
            failedStepsRun.head.copy(logs = (failedStepsRun.head.logs :+ ColoredLogInstruction(s"   Concurrently bloc failed", RED)) ++ logNonExecutedStep(nextSteps))
        }

      case execStep: ExecutableStep[_] ⇒
        runStepAction(execStep)(session) match {
          case Xor.Left(e) ⇒
            val updatedLogs = logs ++ logStepErrorResult(execStep.title, e, RED) ++ logNonExecutedStep(steps.tail)
            buildFailedRunSteps(steps, execStep, e, updatedLogs)

          case Xor.Right(currentSession) ⇒
            val updatedLogs = if (execStep.show) logs :+ ColoredLogInstruction(s"   ${execStep.title}", GREEN) else logs
            runSteps(steps.tail, currentSession, updatedLogs)
        }
    }

  private[cornichon] def runStepAction[A](step: ExecutableStep[A])(implicit session: Session): Xor[CornichonError, Session] =
    Try { step.action(session) } match {
      case Success((newSession, stepAssertion)) ⇒ runStepPredicate(step.negate, newSession, stepAssertion)
      case Failure(e) ⇒
        e match {
          case ce: CornichonError ⇒ left(ce)
          case _                  ⇒ left(StepExecutionError(e))
        }
    }

  private[cornichon] def runStepPredicate[A](negateStep: Boolean, newSession: Session, stepAssertion: StepAssertion[A]): Xor[CornichonError, Session] = {
    val succeedAsExpected = stepAssertion.isSuccess && !negateStep
    val failedAsExpected = !stepAssertion.isSuccess && negateStep

    if (succeedAsExpected || failedAsExpected) right(newSession)
    else
      stepAssertion match {
        case SimpleStepAssertion(expected, actual) ⇒
          left(StepAssertionError(expected, actual, negateStep))
        case DetailedStepAssertion(expected, actual, details) ⇒
          left(DetailedStepAssertionError(actual, details))
      }
  }

  private[cornichon] def findEnclosedSteps(openingStep: Step, steps: Seq[Step]): Seq[Step] = {
    def findLastEnclosedIndex(openingStep: Step, steps: Seq[Step], index: Int, depth: Int): Int = {
      steps.headOption.fold(index) { head ⇒
        openingStep match {
          case ConcurrentStart(_, _) ⇒
            head match {
              case ConcurrentStop(_) if depth == 0 ⇒
                index
              case ConcurrentStop(_) ⇒
                findLastEnclosedIndex(openingStep, steps.tail, index + 1, depth - 1)
              case ConcurrentStart(_, _) ⇒
                findLastEnclosedIndex(openingStep, steps.tail, index + 1, depth + 1)
              case _ ⇒
                findLastEnclosedIndex(openingStep, steps.tail, index + 1, depth)
            }
          case EventuallyStart(_) ⇒
            head match {
              case EventuallyStop(_) if depth == 0 ⇒
                index
              case EventuallyStop(_) ⇒
                findLastEnclosedIndex(openingStep, steps.tail, index + 1, depth - 1)
              case EventuallyStart(_) ⇒
                findLastEnclosedIndex(openingStep, steps.tail, index + 1, depth + 1)
              case _ ⇒
                findLastEnclosedIndex(openingStep, steps.tail, index + 1, depth)
            }
          case _ ⇒ index
        }
      }
    }

    val closingIndex = findLastEnclosedIndex(openingStep, steps, index = 0, depth = 0)
    if (closingIndex == 0) Seq.empty else steps.take(closingIndex)
  }

  @tailrec
  private[cornichon] final def retryEventuallySteps(steps: Seq[Step], session: Session, logs: Seq[LogInstruction], conf: EventuallyConf): StepsReport = {
    val now = System.nanoTime
    val res = runSteps(steps, session, logs)
    val executionTime = Duration.fromNanos(System.nanoTime - now)
    res match {
      case s @ SuccessRunSteps(_, _) ⇒ s
      case f @ FailedRunSteps(failed, _, _) ⇒
        val remainingTime = conf.maxTime - executionTime
        if (remainingTime.gt(Duration.Zero)) {
          val updatedLogs = logs ++ logStepErrorResult(failed.step.title, failed.error, CYAN)
          Thread.sleep(conf.interval.toMillis)
          retryEventuallySteps(steps, session, updatedLogs, conf.consume(executionTime + conf.interval))
        } else f
    }
  }

  private[cornichon] def logStepErrorResult(stepTitle: String, error: CornichonError, ansiColor: String): Seq[LogInstruction] =
    Seq(ColoredLogInstruction(s"   $stepTitle *** FAILED ***", ansiColor)) ++ error.msg.split('\n').map { m ⇒
      ColoredLogInstruction(s"   $m", ansiColor)
    }

  private[cornichon] def logNonExecutedStep(steps: Seq[Step]): Seq[LogInstruction] =
    steps.collect { case e: ExecutableStep[_] ⇒ e }
      .filter(_.show).map { step ⇒
        ColoredLogInstruction(s"   ${step.title}", CYAN)
      }

  private[cornichon] def buildFailedRunSteps(steps: Seq[Step], currentStep: Step, e: CornichonError, logs: Seq[LogInstruction]): FailedRunSteps = {
    val failedStep = FailedStep(currentStep, e)
    val notExecutedStep = steps.tail.collect { case ExecutableStep(title, _, _, _) ⇒ title }
    FailedRunSteps(failedStep, notExecutedStep, logs)
  }
}