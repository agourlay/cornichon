package com.github.agourlay.cornichon.steps.wrapped

import cats.data.Xor
import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.core.Done._
import cats.data.Xor._

import scala.concurrent.{ Await, ExecutionContext, Future }
import scala.concurrent.duration.Duration
import scala.util.{ Failure, Success, Try }

case class ConcurrentlyStep(nested: Vector[Step], factor: Int, maxTime: Duration) extends WrapperStep {

  require(factor > 0, "concurrently block must contain a positive factor")

  val title = s"Concurrently block with factor '$factor' and maxTime '$maxTime'"

  override def run(engine: Engine)(initialRunState: RunState)(implicit ec: ExecutionContext) = {
    val nestedRunState = initialRunState.withSteps(nested).resetLogs.goDeeper
    val start = System.nanoTime
    val f = Future.traverse(List.fill(factor)(nested)) { steps ⇒
      Future { engine.runSteps(nestedRunState) }
    }

    val initialDepth = initialRunState.depth

    val results = Try { Await.result(f, maxTime) } match {
      case Success(s) ⇒
        s
      case Failure(e) ⇒
        val failedStep = FailedStep(this, ConcurrentlyTimeout)
        List((nestedRunState.appendLog(failedTitleLog(initialDepth)), left(failedStep)))
    }

    // Only the first error report found is used in the logs.
    val failedStepRun = results.collectFirst { case (s, r @ Xor.Left(_)) ⇒ (s, r) }
    failedStepRun.fold[(RunState, Xor[FailedStep, Done])] {
      val executionTime = Duration.fromNanos(System.nanoTime - start)
      val successStepsRun = results.collect { case (s, r @ Xor.Right(_)) ⇒ (s, r) }
      // all runs were successfull, we pick the first one
      val resultState = successStepsRun.head._1
      //TODO all sessions should be merged?
      val updatedSession = resultState.session
      //TODO all logs should be merged?
      val updatedLogs = successTitleLog(initialDepth) +: resultState.logs :+ SuccessLogInstruction(s"Concurrently block with factor '$factor' succeeded", initialDepth, Some(executionTime))
      (initialRunState.withSession(updatedSession).appendLogs(updatedLogs), rightDone)
    } {
      case (s, failedXor) ⇒
        val updatedLogs = failedTitleLog(initialDepth) +: s.logs :+ FailureLogInstruction(s"Concurrently block failed", initialDepth)
        (initialRunState.withSession(s.session).appendLogs(updatedLogs), failedXor)
    }
  }
}

case object ConcurrentlyTimeout extends CornichonError {
  val msg = "concurrent block did not reach completion in 'maxTime'"
}
