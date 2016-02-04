package com.github.agourlay.cornichon.core

import cats.data.Xor
import cats.data.Xor.{ left, right }
import com.github.agourlay.cornichon.dsl._
import com.github.agourlay.cornichon.core.ScenarioReport._

import scala.annotation.tailrec
import scala.concurrent.{ ExecutionContext, Await, Future }
import scala.concurrent.duration.Duration
import scala.util._

class Engine(executionContext: ExecutionContext) {

  private implicit val ec = executionContext

  def runScenario(session: Session, finallySteps: Seq[Step] = Seq.empty)(scenario: Scenario): ScenarioReport = {
    val initMargin = 1
    val titleLog = Vector(DefaultLogInstruction(s"Scenario : ${scenario.name}", initMargin))
    val mainExecution = fromStepsReport(scenario, runSteps(scenario.steps, session, titleLog, initMargin + 1))
    if (finallySteps.isEmpty)
      mainExecution
    else {
      val finallyExecution = fromStepsReport(scenario, runSteps(finallySteps.toVector, mainExecution.session, Vector.empty, initMargin + 1))
      mainExecution.merge(finallyExecution)
    }
  }

  private[cornichon] def runSteps(steps: Vector[Step], session: Session, logs: Vector[LogInstruction], depth: Int): StepsReport =
    steps.headOption.fold[StepsReport](SuccessRunSteps(session, logs)) {
      case d @ DebugStep(message) ⇒
        Try { message(session) } match {
          case Success(debugMessage) ⇒
            val updatedLogs = logs :+ InfoLogInstruction(message(session), depth)
            runSteps(steps.tail, session, updatedLogs, depth)
          case Failure(e) ⇒
            val cornichonError = toCornichonError(e)
            val updatedLogs = logs ++ errorLogs(d.title, cornichonError, depth, steps.tail)
            buildFailedRunSteps(steps, d, cornichonError, updatedLogs, session)
        }

      case e @ EventuallyStart(conf) ⇒
        val updatedLogs = logs :+ DefaultLogInstruction(e.title, depth)
        val eventuallySteps = findEnclosedSteps(e, steps.tail)

        if (eventuallySteps.isEmpty) {
          val updatedLogs = logs ++ errorLogs(e.title, MalformedEventuallyBlock, depth, steps.tail)
          buildFailedRunSteps(steps, e, MalformedEventuallyBlock, updatedLogs, session)
        } else {
          val start = System.nanoTime
          val res = retryEventuallySteps(eventuallySteps, session, conf, Vector.empty, depth + 1)
          val executionTime = Duration.fromNanos(System.nanoTime - start)
          val nextSteps = steps.tail.drop(eventuallySteps.size)
          res match {
            case s @ SuccessRunSteps(sSession, sLogs) ⇒
              val fullLogs = updatedLogs ++ sLogs :+ SuccessLogInstruction(s"Eventually block succeeded", depth, Some(executionTime))
              runSteps(nextSteps, sSession, fullLogs, depth)
            case f @ FailedRunSteps(_, _, eLogs, fSession) ⇒
              val fullLogs = (updatedLogs ++ eLogs :+ FailureLogInstruction(s"Eventually block did not complete in time", depth, Some(executionTime))) ++ logNonExecutedStep(nextSteps, depth)
              f.copy(logs = fullLogs, session = fSession)
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
          val innerLogs = logs ++ errorLogs(c.title, MalformedConcurrentBlock, depth, steps.tail)
          buildFailedRunSteps(steps, c, MalformedConcurrentBlock, innerLogs, session)
        } else {
          val start = System.nanoTime
          val f = Future.traverse(List.fill(factor)(concurrentSteps)) { steps ⇒
            Future { runSteps(steps, session, updatedLogs, depth + 1) }
          }

          val results = Try { Await.result(f, maxTime) } match {
            case Success(s) ⇒ s
            case Failure(e) ⇒ List(buildFailedRunSteps(steps, c, ConcurrentlyTimeout, updatedLogs, session))
          }

          val failedStepRun = results.collectFirst { case f @ FailedRunSteps(_, _, _, _) ⇒ f }
          val nextSteps = steps.tail.drop(concurrentSteps.size)
          failedStepRun.fold {
            val executionTime = Duration.fromNanos(System.nanoTime - start)
            val successStepsRun = results.collect { case s @ SuccessRunSteps(_, _) ⇒ s }
            val updatedSession = successStepsRun.head.session
            val updatedLogs = successStepsRun.head.logs :+ SuccessLogInstruction(s"Concurrently block with factor '$factor' succeeded", depth, Some(executionTime))
            runSteps(nextSteps, updatedSession, updatedLogs, depth)
          } { f ⇒
            f.copy(logs = (f.logs :+ FailureLogInstruction(s"Concurrently block failed", depth)) ++ logNonExecutedStep(nextSteps, depth))
          }
        }

      case a @ AssertStep(title, toAssertion, negate, show) ⇒
        val res = Xor.catchNonFatal(toAssertion(session))
          .leftMap(toCornichonError)
          .flatMap { assertion ⇒
            runStepPredicate(negate, session, assertion)
          }
        buildStepReport(steps, session, logs, res, title, depth, show)

      case e @ EffectStep(title, effect, show) ⇒
        val start = System.nanoTime
        val res = Xor.catchNonFatal(effect(session)).leftMap(toCornichonError)
        val executionTime = Duration.fromNanos(System.nanoTime - start)
        buildStepReport(steps, session, logs, res, title, depth, show, Some(executionTime))

    }

  private def buildStepReport(steps: Vector[Step], session: Session, logs: Vector[LogInstruction], res: Xor[CornichonError, Session], title: String, depth: Int, show: Boolean, duration: Option[Duration] = None) =
    res match {
      case Xor.Left(e) ⇒
        val updatedLogs = logs ++ errorLogs(title, e, depth, steps.tail)
        buildFailedRunSteps(steps, steps.head, e, updatedLogs, session)

      case Xor.Right(currentSession) ⇒
        val updatedLogs = if (show) logs :+ SuccessLogInstruction(title, depth, duration) else logs
        runSteps(steps.tail, currentSession, updatedLogs, depth)
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
      case s @ SuccessRunSteps(successSession, sLogs) ⇒
        val runLogs = accLogs ++ sLogs
        if (remainingTime.gt(Duration.Zero)) s.copy(logs = runLogs)
        else buildFailedRunSteps(steps, steps.last, EventuallyBlockSucceedAfterMaxDuration, runLogs, successSession)
      case f @ FailedRunSteps(failed, _, fLogs, fSession) ⇒
        val updatedLogs = accLogs ++ fLogs
        if ((remainingTime - conf.interval).gt(Duration.Zero)) {
          Thread.sleep(conf.interval.toMillis)
          retryEventuallySteps(steps, session, conf.consume(executionTime + conf.interval), updatedLogs, depth)
        } else f.copy(logs = updatedLogs, session = fSession)
    }
  }

  private[cornichon] def logNonExecutedStep(steps: Seq[Step], depth: Int): Seq[LogInstruction] =
    steps.collect {
      case a @ AssertStep(_, _, _, true) ⇒ a
      case e @ EffectStep(_, _, true)    ⇒ e
    }.map { step ⇒
      InfoLogInstruction(step.title, depth)
    }

  private[cornichon] def errorLogs(title: String, error: CornichonError, depth: Int, remainingSteps: Vector[Step]) = {
    val logStepErrorResult = Vector(FailureLogInstruction(s"$title *** FAILED ***", depth)) ++ error.msg.split('\n').map { m ⇒
      FailureLogInstruction(m, depth)
    }
    logStepErrorResult ++ logNonExecutedStep(remainingSteps, depth)
  }

  private[cornichon] def buildFailedRunSteps(steps: Vector[Step], currentStep: Step, e: CornichonError, logs: Vector[LogInstruction], session: Session): FailedRunSteps = {
    val failedStep = FailedStep(currentStep, e)
    val notExecutedStep = steps.tail.collect {
      case AssertStep(t, _, _, true) ⇒ t
      case EffectStep(t, _, true)    ⇒ t
    }
    FailedRunSteps(failedStep, notExecutedStep, logs, session)
  }
}