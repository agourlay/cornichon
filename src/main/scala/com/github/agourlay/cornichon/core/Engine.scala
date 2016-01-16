package com.github.agourlay.cornichon.core

import cats.data.Xor
import cats.data.Xor.{ left, right }
import com.github.agourlay.cornichon.dsl._

import scala.Console._
import scala.annotation.tailrec
import scala.concurrent.{ ExecutionContext, Await, Future }
import scala.concurrent.duration.Duration
import scala.util._

class Engine(executionContext: ExecutionContext) {

  private implicit val ec = executionContext

  def runScenario(session: Session)(scenario: Scenario): ScenarioReport = {
    val initMargin = 1
    val initLogs = Vector(DefaultLogInstruction(s"Scenario : ${scenario.name}", initMargin))
    runSteps(scenario.steps, session, initLogs, initMargin + 1) match {
      case s @ SuccessRunSteps(_, _)   ⇒ SuccessScenarioReport(scenario, s)
      case f @ FailedRunSteps(_, _, _) ⇒ FailedScenarioReport(scenario, f)
    }
  }

  private[cornichon] def runSteps(steps: Vector[Step], session: Session, logs: Vector[LogInstruction], depth: Int): StepsReport =
    steps.headOption.fold[StepsReport](SuccessRunSteps(session, logs)) {
      case d @ DebugStep(message) ⇒
        Try { message(session) } match {
          case Success(debugMessage) ⇒
            val updatedLogs = logs :+ ColoredLogInstruction(message(session), CYAN, depth)
            runSteps(steps.tail, session, updatedLogs, depth)
          case Failure(e) ⇒
            val cornichonError = toCornichonError(e)
            val updatedLogs = logs ++ logStepErrorResult(d.title, cornichonError, RED, depth) ++ logNonExecutedStep(steps.tail, depth)
            buildFailedRunSteps(steps, d, cornichonError, updatedLogs)
        }

      case e @ EventuallyStart(conf) ⇒
        val updatedLogs = logs :+ DefaultLogInstruction(e.title, depth)
        val eventuallySteps = findEnclosedSteps(e, steps.tail)

        if (eventuallySteps.isEmpty) {
          val updatedLogs = logs ++ logStepErrorResult(e.title, MalformedEventuallyBlock, RED, depth) ++ logNonExecutedStep(steps.tail, depth)
          buildFailedRunSteps(steps, e, MalformedEventuallyBlock, updatedLogs)
        } else {
          val nextDepth = depth + 1
          val start = System.nanoTime
          val res = retryEventuallySteps(eventuallySteps, session, conf, Vector.empty, nextDepth)
          val executionTime = Duration.fromNanos(System.nanoTime - start)
          val nextSteps = steps.tail.drop(eventuallySteps.size)
          res match {
            case s @ SuccessRunSteps(sSession, sLogs) ⇒
              val fullLogs = updatedLogs ++ sLogs :+ ColoredLogInstruction(s"Eventually block succeeded in ${executionTime.toMillis} millis.", GREEN, nextDepth)
              runSteps(nextSteps, sSession, fullLogs, depth)
            case f @ FailedRunSteps(_, _, eLogs) ⇒
              val fullLogs = (updatedLogs ++ eLogs :+ ColoredLogInstruction(s"Eventually block did not complete in time. (${executionTime.toMillis} millis) ", RED, nextDepth)) ++ logNonExecutedStep(nextSteps, depth)
              f.copy(logs = fullLogs)
          }
        }

      case EventuallyStop(conf) ⇒
        runSteps(steps.tail, session, logs, depth)

      case ConcurrentStop(factor) ⇒
        runSteps(steps.tail, session, logs, depth)

      case c @ ConcurrentStart(factor, maxTime) ⇒
        val updatedLogs = logs :+ DefaultLogInstruction(c.title, depth)
        val concurrentSteps = findEnclosedSteps(c, steps.tail)

        if (concurrentSteps.isEmpty) {
          val innerLogs = logs ++ logStepErrorResult(c.title, MalformedConcurrentBlock, RED, depth) ++ logNonExecutedStep(steps.tail, depth)
          buildFailedRunSteps(steps, c, MalformedConcurrentBlock, innerLogs)
        } else {
          val nextDepth = depth + 1
          val start = System.nanoTime
          val f = Future.traverse(List.fill(factor)(concurrentSteps)) { steps ⇒
            Future { runSteps(steps, session, updatedLogs, nextDepth) }
          }

          val results = Try { Await.result(f, maxTime) } match {
            case Success(s) ⇒ s
            case Failure(e) ⇒ List(buildFailedRunSteps(steps, c, ConcurrentlyTimeout, updatedLogs))
          }

          val failedStepRun = results.collectFirst { case f @ FailedRunSteps(_, _, _) ⇒ f }
          val nextSteps = steps.tail.drop(concurrentSteps.size)
          failedStepRun.fold {
            val executionTime = Duration.fromNanos(System.nanoTime - start)
            val successStepsRun = results.collect { case s @ SuccessRunSteps(_, _) ⇒ s }
            val updatedSession = successStepsRun.head.session
            val updatedLogs = successStepsRun.head.logs :+ ColoredLogInstruction(s"Concurrently block with factor '$factor' succeeded in ${executionTime.toMillis} millis.", GREEN, nextDepth)
            runSteps(nextSteps, updatedSession, updatedLogs, depth)
          } { f ⇒
            f.copy(logs = (f.logs :+ ColoredLogInstruction(s"Concurrently block failed", RED, nextDepth)) ++ logNonExecutedStep(nextSteps, depth))
          }
        }

      case execStep: RunnableStep[_] ⇒
        runStepAction(execStep)(session) match {
          case Xor.Left(e) ⇒
            val updatedLogs = logs ++ logStepErrorResult(execStep.title, e, RED, depth) ++ logNonExecutedStep(steps.tail, depth)
            buildFailedRunSteps(steps, execStep, e, updatedLogs)

          case Xor.Right(currentSession) ⇒
            val updatedLogs = if (execStep.show) logs :+ ColoredLogInstruction(execStep.title, GREEN, depth) else logs
            runSteps(steps.tail, currentSession, updatedLogs, depth)
        }
    }

  private[cornichon] def runStepAction[A](step: RunnableStep[A])(implicit session: Session): Xor[CornichonError, Session] =
    Try { step.action(session) } match {
      case Success((newSession, stepAssertion)) ⇒ runStepPredicate(step.negate, newSession, stepAssertion)
      case Failure(e)                           ⇒ left(toCornichonError(e))
    }

  private[cornichon] def toCornichonError(exception: Throwable): CornichonError = exception match {
    case ce: CornichonError ⇒ ce
    case _                  ⇒ StepExecutionError(exception)
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

  //TODO remove duplication
  private[cornichon] def findEnclosedSteps(openingStep: Step, steps: Vector[Step]): Vector[Step] = {
    def findLastEnclosedIndex(openingStep: Step, steps: Vector[Step], index: Int, depth: Int): Int = {
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
    if (closingIndex == 0) Vector.empty else steps.take(closingIndex)
  }

  @tailrec
  private[cornichon] final def retryEventuallySteps(steps: Vector[Step], session: Session, conf: EventuallyConf, accLogs: Vector[LogInstruction], depth: Int): StepsReport = {
    val now = System.nanoTime
    val res = runSteps(steps, session, Vector.empty, depth)
    val executionTime = Duration.fromNanos(System.nanoTime - now)
    val remainingTime = conf.maxTime - executionTime
    res match {
      case s @ SuccessRunSteps(_, sLogs) ⇒
        val runLogs = accLogs ++ sLogs
        if (remainingTime.gt(Duration.Zero)) s.copy(logs = runLogs)
        else buildFailedRunSteps(steps, steps.last, EventuallyBlockSucceedAfterMaxDuration, runLogs)
      case f @ FailedRunSteps(failed, _, fLogs) ⇒
        val updatedLogs = accLogs ++ fLogs
        if ((remainingTime - conf.interval).gt(Duration.Zero)) {
          Thread.sleep(conf.interval.toMillis)
          retryEventuallySteps(steps, session, conf.consume(executionTime + conf.interval), updatedLogs, depth)
        } else f.copy(logs = updatedLogs)
    }
  }

  private[cornichon] def logStepErrorResult(stepTitle: String, error: CornichonError, ansiColor: String, depth: Int): Vector[LogInstruction] =
    Vector(ColoredLogInstruction(s"$stepTitle *** FAILED ***", ansiColor, depth)) ++ error.msg.split('\n').map { m ⇒
      ColoredLogInstruction(m, ansiColor, depth)
    }

  private[cornichon] def logNonExecutedStep(steps: Seq[Step], depth: Int): Seq[LogInstruction] =
    steps.collect { case e: RunnableStep[_] ⇒ e }
      .filter(_.show).map { step ⇒
        ColoredLogInstruction(step.title, CYAN, depth)
      }

  private[cornichon] def buildFailedRunSteps(steps: Vector[Step], currentStep: Step, e: CornichonError, logs: Vector[LogInstruction]): FailedRunSteps = {
    val failedStep = FailedStep(currentStep, e)
    val notExecutedStep = steps.tail.collect { case RunnableStep(title, _, _, _) ⇒ title }
    FailedRunSteps(failedStep, notExecutedStep, logs)
  }
}