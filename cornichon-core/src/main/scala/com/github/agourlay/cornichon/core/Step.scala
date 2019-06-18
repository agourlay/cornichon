package com.github.agourlay.cornichon.core

import cats.data.{ NonEmptyList, StateT }
import com.github.agourlay.cornichon.core.Done._
import com.github.agourlay.cornichon.core.core.{ StepResult, StepState }
import com.github.agourlay.cornichon.steps.wrapped.FlatMapStep
import monix.eval.Task

import scala.concurrent.duration.Duration

sealed trait Step {
  def title: String
  def setTitle(newTitle: String): Step
  def onEngine(engine: Engine): StepState
  def runOnEngine(engine: Engine, initialRunState: RunState): StepResult = onEngine(engine).run(initialRunState)
  def chain(others: Session ⇒ List[Step]): Step = FlatMapStep(this, others)
  def andThen(others: List[Step]): Step = FlatMapStep(this, _ ⇒ others)
  def andThen(other: Step): Step = FlatMapStep(this, _ ⇒ other :: Nil)
}

object NoOpStep extends Step {
  def title: String = "noOp"
  def setTitle(newTitle: String): Step = this
  def onEngine(engine: Engine): StepState = StateT { initialRunState ⇒
    Task.now(initialRunState -> rightDone)
  }
}

//Step that produces a Session
trait SessionValueStep extends Step {

  def run(initialRunState: RunState): Task[NonEmptyList[CornichonError] Either Session]

  def onError(errors: NonEmptyList[CornichonError], initialRunState: RunState): (List[LogInstruction], FailedStep)

  def logOnSuccess(result: Session, initialRunState: RunState, executionTime: Duration): LogInstruction

  def onEngine(engine: Engine): StepState = StateT { initialRunState ⇒
    val now = System.nanoTime
    run(initialRunState).map {
      case Left(errors) ⇒
        val (logs, failedStep) = onError(errors, initialRunState)
        (initialRunState.recordLogStack(logs), Left(failedStep))

      case Right(session) ⇒
        val executionTime = Duration.fromNanos(System.nanoTime - now)
        val log = logOnSuccess(session, initialRunState, executionTime)
        val logSessionState = initialRunState.recordLog(log).withSession(session)
        (logSessionState, rightDone)
    }
  }
}

//Step that produces a value to create a log
trait LogValueStep[A] extends Step {

  def run(initialRunState: RunState): Task[NonEmptyList[CornichonError] Either A]

  def onError(errors: NonEmptyList[CornichonError], initialRunState: RunState): (List[LogInstruction], FailedStep)

  def logOnSuccess(result: A, initialRunState: RunState, executionTime: Duration): LogInstruction

  def onEngine(engine: Engine): StepState = StateT { initialRunState ⇒
    val now = System.nanoTime
    run(initialRunState).map {
      case Left(errors) ⇒
        val (logStack, failedStep) = onError(errors, initialRunState)
        (initialRunState.recordLogStack(logStack), Left(failedStep))

      case Right(value) ⇒
        val executionTime = Duration.fromNanos(System.nanoTime - now)
        val log = logOnSuccess(value, initialRunState, executionTime)
        val logState = initialRunState.recordLog(log)
        (logState, rightDone)
    }
  }
}

//Step that delegate the execution of nested steps and enable to decorate the nestedLogs
trait LogDecoratorStep extends Step {

  def nestedToRun: Session ⇒ List[Step]

  def logStackOnNestedError(resultLogStack: List[LogInstruction], depth: Int, executionTime: Duration): List[LogInstruction]

  def logStackOnNestedSuccess(resultLogStack: List[LogInstruction], depth: Int, executionTime: Duration): List[LogInstruction]

  def onEngine(engine: Engine): StepState = StateT { initialRunState ⇒
    val now = System.nanoTime
    val steps = nestedToRun(initialRunState.session)
    engine.runStepsShortCircuiting(steps, initialRunState.nestedContext).map {
      case (resState, l @ Left(_)) ⇒
        val executionTime = Duration.fromNanos(System.nanoTime - now)
        val decoratedLogs = logStackOnNestedError(resState.logStack, initialRunState.depth, executionTime)
        (initialRunState.mergeNested(resState, decoratedLogs), l)

      case (resState, r @ Right(_)) ⇒
        val executionTime = Duration.fromNanos(System.nanoTime - now)
        val decoratedLogs = logStackOnNestedSuccess(resState.logStack, initialRunState.depth, executionTime)
        (initialRunState.mergeNested(resState, decoratedLogs), r)
    }
  }

  // Without effect by default - wrapper steps usually build dynamically their title
  def setTitle(newTitle: String): Step = this
  def failedTitleLog(depth: Int) = FailureLogInstruction(title, depth)
  def successTitleLog(depth: Int) = SuccessLogInstruction(title, depth)
}

//Step that delegate the execution of nested steps and enable to inspect RunState and FailedStep
trait SimpleWrapperStep extends Step {

  def nestedToRun: List[Step]

  def indentLog: Boolean = true

  def onNestedError(failedStep: FailedStep, resultRunState: RunState, initialRunState: RunState, executionTime: Duration): (RunState, FailedStep)

  def onNestedSuccess(resultRunState: RunState, initialRunState: RunState, executionTime: Duration): RunState

  def onEngine(engine: Engine): StepState = StateT { initialRunState ⇒
    val now = System.nanoTime
    val init = if (indentLog) initialRunState.nestedContext else initialRunState.sameLevelContext
    engine.runStepsShortCircuiting(nestedToRun, init).map {
      case (resState, Left(failedStep)) ⇒
        val executionTime = Duration.fromNanos(System.nanoTime - now)
        val (finalState, fs) = onNestedError(failedStep, resState, initialRunState, executionTime)
        (finalState, Left(fs))

      case (resState, Right(_)) ⇒
        val executionTime = Duration.fromNanos(System.nanoTime - now)
        val finalState = onNestedSuccess(resState, initialRunState, executionTime)
        (finalState, rightDone)
    }
  }

  // Without effect by default - wrapper steps usually build dynamically their title
  def setTitle(newTitle: String): Step = this
  def failedTitleLog(depth: Int) = FailureLogInstruction(title, depth)
  def successTitleLog(depth: Int) = SuccessLogInstruction(title, depth)
}

//Step that gives full control over the execution of nested steps and their error reporting
trait WrapperStep extends Step {
  // Without effect by default - wrapper steps usually build dynamically their title
  def setTitle(newTitle: String): Step = this
  def failedTitleLog(depth: Int) = FailureLogInstruction(title, depth)
  def successTitleLog(depth: Int) = SuccessLogInstruction(title, depth)
}

