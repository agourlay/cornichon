package com.github.agourlay.cornichon.steps.wrapped

import com.github.agourlay.cornichon.core.Done._
import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.dsl.BlockScopedResource

import monix.execution.Scheduler

case class WithBlockScopedResource(nested: List[Step], resource: BlockScopedResource) extends WrapperStep {

  val title = resource.openingTitle

  override def run(engine: Engine)(initialRunState: RunState)(implicit scheduler: Scheduler) = {

    for {
      resourceHandle ← resource.startResource()
      resourcedRunState = initialRunState.forNestedSteps(nested).mergeSessions(resourceHandle.initialisedSession)
      (resourcedState, resourcedRes) ← engine.runSteps(resourcedRunState)
      (fullLogs, xor) = {
        val nestedLogs = resourcedState.logs
        val initialDepth = initialRunState.depth
        resourcedRes.fold(
          failedStep ⇒ {
            val failureLogs = failedTitleLog(initialDepth) +: nestedLogs :+ FailureLogInstruction(resource.closingTitle, initialDepth)
            (failureLogs, Left(failedStep))
          },
          _ ⇒ {
            val successLogs = successTitleLog(initialDepth) +: nestedLogs :+ SuccessLogInstruction(resource.closingTitle, initialDepth, None)
            (successLogs, rightDone)
          }
        )
      }
      results ← resourceHandle.resourceResults()
      _ ← resourceHandle.stopResource()
    } yield {
      val completeSession = resourcedState.session.merge(results)
      (initialRunState.withSession(completeSession).appendLogs(fullLogs), xor)
    }
  }
}
