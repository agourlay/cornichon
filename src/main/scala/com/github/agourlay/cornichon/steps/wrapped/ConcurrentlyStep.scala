package com.github.agourlay.cornichon.steps.wrapped

import java.util.Timer

import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.core.Done._
import com.github.agourlay.cornichon.util.Timeouts

import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration.{ Duration, FiniteDuration }
import scala.util.control.NonFatal

case class ConcurrentlyStep(nested: List[Step], factor: Int, maxTime: FiniteDuration) extends WrapperStep {

  require(factor > 0, "concurrently block must contain a positive factor")

  val title = s"Concurrently block with factor '$factor' and maxTime '$maxTime'"

  override def run(engine: Engine)(initialRunState: RunState)(implicit ec: ExecutionContext, timer: Timer) = {
    val nestedRunState = initialRunState.forNestedSteps(nested)
    val initialDepth = initialRunState.depth
    val start = System.nanoTime
    val f = Future.traverse(List.fill(factor)(nested)) { steps ⇒
      engine.runSteps(nestedRunState)
    }

    Timeouts.failAfter(maxTime)(f)(ConcurrentlyTimeout).flatMap { results ⇒
      // Only the first error report found is used in the logs.
      val failedStepRun = results.collectFirst { case (s, r @ Left(_)) ⇒ (s, r) }
      failedStepRun.fold[Future[(RunState, Either[FailedStep, Done])]] {
        val executionTime = Duration.fromNanos(System.nanoTime - start)
        val successStepsRun = results.collect { case (s, r @ Right(_)) ⇒ (s, r) }
        // all runs were successfull, we pick the first one
        val resultState = successStepsRun.head._1
        //TODO all sessions should be merged?
        val updatedSession = resultState.session
        //TODO all logs should be merged?
        val updatedLogs = successTitleLog(initialDepth) +: resultState.logs :+ SuccessLogInstruction(s"Concurrently block with factor '$factor' succeeded", initialDepth, Some(executionTime))
        Future.successful(initialRunState.withSession(updatedSession).appendLogs(updatedLogs), rightDone)
      } {
        case (s, failedXor) ⇒
          val updatedLogs = failedTitleLog(initialDepth) +: s.logs :+ FailureLogInstruction(s"Concurrently block failed", initialDepth)
          Future.successful(initialRunState.withSession(s.session).appendLogs(updatedLogs), failedXor)
      }.recover {
        case NonFatal(e) ⇒
          val failedStep = FailedStep(this, ConcurrentlyTimeout)
          (nestedRunState.appendLog(failedTitleLog(initialDepth)), Left(failedStep))
      }
    }
  }
}

case object ConcurrentlyTimeout extends CornichonError {
  val msg = "concurrent block did not reach completion in 'maxTime'"
}
