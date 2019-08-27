package com.github.agourlay.cornichon.core

import cats.data.{ NonEmptyList, StateT }
import com.github.agourlay.cornichon.core.Done._
import com.github.agourlay.cornichon.steps.wrapped.FlatMapStep
import monix.eval.Task

import scala.concurrent.duration.Duration

sealed trait Step {
  def title: String
  def setTitle(newTitle: String): Step
  val stateUpdate: StepState
  def runStep(runState: RunState): StepResult = stateUpdate.run(runState)
  def chain(others: Session ⇒ List[Step]): Step = FlatMapStep(this, others)
  def andThen(others: List[Step]): Step = FlatMapStep(this, _ ⇒ others)
  def andThen(other: Step): Step = FlatMapStep(this, _ ⇒ other :: Nil)
}

object NoOpStep extends Step {
  val title: String = "noOp"
  def setTitle(newTitle: String): Step = this
  val stateUpdate: StepState = StateT { runState ⇒ Task.now(runState -> rightDone) }
}

//Step that produces a Session
trait SessionValueStep extends Step {

  def runSessionValueStep(runState: RunState): Task[NonEmptyList[CornichonError] Either Session]

  def onError(errors: NonEmptyList[CornichonError], runState: RunState, executionTime: Duration): (LogInstruction, FailedStep)

  def logOnSuccess(result: Session, runState: RunState, executionTime: Duration): LogInstruction

  override val stateUpdate: StepState = StateT { runState ⇒
    runSessionValueStep(runState).timed.map {
      case (executionTime, Left(errors)) ⇒
        val (logs, failedStep) = onError(errors, runState, executionTime)
        (runState.recordLog(logs), Left(failedStep))

      case (executionTime, Right(session)) ⇒
        val log = logOnSuccess(session, runState, executionTime)
        val logSessionState = runState.recordLog(log).withSession(session)
        (logSessionState, rightDone)
    }
  }
}

//Step that produces a value to create a log
trait LogValueStep[A] extends Step {

  def runLogValueStep(runState: RunState): Task[NonEmptyList[CornichonError] Either A]

  def onError(errors: NonEmptyList[CornichonError], runState: RunState, executionTime: Duration): (LogInstruction, FailedStep)

  def logOnSuccess(result: A, runState: RunState, executionTime: Duration): LogInstruction

  override val stateUpdate: StepState = StateT { rs ⇒
    runLogValueStep(rs).timed.map {
      case (executionTime, Left(errors)) ⇒
        val (logStack, failedStep) = onError(errors, rs, executionTime)
        (rs.recordLog(logStack), Left(failedStep))

      case (executionTime, Right(value)) ⇒
        val log = logOnSuccess(value, rs, executionTime)
        val logState = rs.recordLog(log)
        (logState, rightDone)
    }
  }
}

//Step that delegate the execution of nested steps and enable to decorate the nestedLogs
trait LogDecoratorStep extends Step {

  def nestedToRun: Session ⇒ List[Step]

  def logStackOnNestedError(resultLogStack: List[LogInstruction], depth: Int, executionTime: Duration): List[LogInstruction]

  def logStackOnNestedSuccess(resultLogStack: List[LogInstruction], depth: Int, executionTime: Duration): List[LogInstruction]

  override val stateUpdate: StepState = StateT { rs ⇒
    val steps = nestedToRun(rs.session)
    ScenarioRunner.runStepsShortCircuiting(steps, rs.nestedContext).timed.map {
      case (executionTime, (resState, l @ Left(_))) ⇒
        val decoratedLogs = logStackOnNestedError(resState.logStack, rs.depth, executionTime)
        (rs.mergeNested(resState, decoratedLogs), l)

      case (executionTime, (resState, r @ Right(_))) ⇒
        val decoratedLogs = logStackOnNestedSuccess(resState.logStack, rs.depth, executionTime)
        (rs.mergeNested(resState, decoratedLogs), r)
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

  def onNestedError(failedStep: FailedStep, resultRunState: RunState, runState: RunState, executionTime: Duration): (RunState, FailedStep)

  def onNestedSuccess(resultRunState: RunState, runState: RunState, executionTime: Duration): RunState

  override val stateUpdate: StepState = StateT { rs ⇒
    val init = if (indentLog) rs.nestedContext else rs.sameLevelContext
    ScenarioRunner.runStepsShortCircuiting(nestedToRun, init).timed.map {
      case (executionTime, (resState, Left(failedStep))) ⇒
        val (finalState, fs) = onNestedError(failedStep, resState, rs, executionTime)
        (finalState, Left(fs))

      case (executionTime, (resState, Right(_))) ⇒
        val finalState = onNestedSuccess(resState, rs, executionTime)
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

