package com.github.agourlay.cornichon.steps.wrapped

import cats.syntax.monoid._

import com.github.agourlay.cornichon.core.Done._
import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.dsl.BlockScopedResource

case class WithBlockScopedResource(nested: List[Step], resource: BlockScopedResource) extends WrapperStep {

  val title = resource.openingTitle

  override def run(engine: Engine)(initialRunState: RunState) = {

    for {
      resourceHandle ← resource.startResource()
      resourcedRunState = initialRunState.forNestedSteps(nested).mergeSessions(resourceHandle.initialisedSession)
      resTuple ← engine.runSteps(resourcedRunState)
      (resourcedState, resourcedRes) = resTuple
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
      val completeSession = resourcedState.session.combine(results)
      (initialRunState.withSession(completeSession).appendLogs(fullLogs), xor)
    }
  }
}
