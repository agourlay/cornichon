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

  def run(engine: Engine, session: Session, depth: Int)(implicit ec: ExecutionContext) = {
    val start = System.nanoTime
    val f = Future.traverse(List.fill(factor)(nested)) { steps ⇒
      Future { engine.runSteps(steps, session, Vector.empty, depth + 1) }
    }

    val results = Try { Await.result(f, maxTime) } match {
      case Success(s) ⇒
        s
      case Failure(e) ⇒
        val failedStep = FailedStep(this, ConcurrentlyTimeout)
        List((session, Vector(failedTitleLog(depth)), left(failedStep)))
    }

    // Only the first error report found is used in the logs.
    val failedStepRun = results.collectFirst { case (s, l, r @ Xor.Left(_)) ⇒ (s, l, r) }
    failedStepRun.fold[(Session, Vector[LogInstruction], Xor[FailedStep, Done])] {
      val executionTime = Duration.fromNanos(System.nanoTime - start)
      val successStepsRun = results.collect { case (s, l, r @ Xor.Right(_)) ⇒ (s, l, r) }
      //TODO all sessions should be merged?
      val updatedSession = successStepsRun.head._1
      //TODO all logs should be merged?
      val updatedLogs = successTitleLog(depth) +: successStepsRun.head._2 :+ SuccessLogInstruction(s"Concurrently block with factor '$factor' succeeded", depth, Some(executionTime))
      (updatedSession, updatedLogs, rightDone)
    } {
      case (s, l, f) ⇒
        val updatedLogs = failedTitleLog(depth) +: l :+ FailureLogInstruction(s"Concurrently block failed", depth)
        (s, updatedLogs, f)
    }
  }
}
