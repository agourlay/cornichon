package com.github.agourlay.cornichon.steps.wrapped

import cats.instances.list._
import cats.syntax.foldable._
import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.core.Done._
import com.github.agourlay.cornichon.core.core.StepResult
import monix.eval.Task
import monix.reactive.Observable

import scala.concurrent.duration.{ Duration, FiniteDuration }
import scala.util.control.NonFatal

case class RepeatConcurrentlyStep(times: Int, nested: List[Step], parallelism: Int, maxTime: FiniteDuration) extends WrapperStep {
  require(parallelism > 0, "repeat concurrently block must contain a positive 'parallelism' factor")
  require(times > 0, "repeat concurrently block must contain a positive 'times' factor")
  require(times >= parallelism, "repeat concurrently block must contain a 'parallelism' factor <= to the number of repeat 'times'")

  val title = s"Repeat concurrently block '$times' times with parallel factor '$parallelism' and maxTime '$maxTime'"

  override def run(engine: Engine)(initialRunState: RunState): StepResult = {
    val nestedRunState = initialRunState.nestedContext
    val initialDepth = initialRunState.depth
    val start = System.nanoTime
    Observable.fromIterable(List.fill(times)(()))
      .mapParallelUnordered(parallelism)(_ ⇒ engine.runSteps(nested, nestedRunState))
      .takeUntil(Observable.evalDelayed(maxTime, ()))
      .toListL
      .flatMap { results ⇒
        if (results.size != times) {
          val error = RepeatConcurrentlyTimeout(times, results.size)
          val errorState = initialRunState.recordLog(failedTitleLog(initialDepth)).recordLog(FailureLogInstruction(error.renderedMessage, initialDepth))
          val failedStep = FailedStep.fromSingle(this, error)
          Task.now(errorState -> Left(failedStep))
        } else {
          val failedStepRuns = results.collect { case (s, r @ Left(_)) ⇒ (s, r) }
          failedStepRuns.headOption.fold[Task[(RunState, Either[FailedStep, Done])]] {
            val executionTime = Duration.fromNanos(System.nanoTime - start)
            val successStepsRun = results.collect { case (s, r @ Right(_)) ⇒ (s, r) }
            val allRunStates = successStepsRun.map(_._1)
            //TODO all logs should be merged?
            // all runs were successful, we pick the first one for the logs
            val firstStateLog = allRunStates.head.logStack
            val wrappedLogStack = SuccessLogInstruction(s"Repeat concurrently block succeeded", initialDepth, Some(executionTime)) +: firstStateLog :+ successTitleLog(initialDepth)
            // TODO merge all sessions together - require diffing Sessions or it produces a huge map full of duplicate as they all started from the same.
            val updatedSession = allRunStates.head.session
            // merge all cleanups steps
            val allCleanupSteps = allRunStates.foldMap(_.cleanupSteps)
            val successState = initialRunState.withSession(updatedSession).recordLogStack(wrappedLogStack).registerCleanupSteps(allCleanupSteps)
            Task.now((successState, rightDone))
          } {
            case (s, failedStep) ⇒
              val ratio = s"'${failedStepRuns.size}/$times' run(s)"
              val wrapLogStack = FailureLogInstruction(s"Repeat concurrently block failed for $ratio", initialDepth) +: s.logStack :+ failedTitleLog(initialDepth)
              Task.now((initialRunState.mergeNested(s, wrapLogStack), failedStep))
          }
        }
      }.onErrorRecover {
        case NonFatal(e) ⇒
          val failedStep = FailedStep.fromSingle(this, RepeatConcurrentlyError(e))
          (initialRunState.recordLog(failedTitleLog(initialDepth)), Left(failedStep))
      }
  }
}

case class RepeatConcurrentlyTimeout(times: Int, success: Int) extends CornichonError {
  lazy val baseErrorMessage = s"Repeat concurrently block did not reach completion in time: $success/$times finished"
}

case class RepeatConcurrentlyError(cause: Throwable) extends CornichonError {
  lazy val baseErrorMessage = "Repeat concurrently block has thrown an error"
  override val causedBy = CornichonError.fromThrowable(cause) :: Nil
}
