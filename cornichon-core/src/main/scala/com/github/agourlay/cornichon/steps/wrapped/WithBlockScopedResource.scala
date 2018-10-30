package com.github.agourlay.cornichon.steps.wrapped

import cats.syntax.monoid._
import com.github.agourlay.cornichon.core.Done._
import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.core.core.StepResult
import com.github.agourlay.cornichon.dsl.BlockScopedResource

case class WithBlockScopedResource(nested: List[Step], resource: BlockScopedResource) extends WrapperStep {

  val title = resource.openingTitle

  override def run(engine: Engine)(initialRunState: RunState): StepResult = {

    for {
      resTuple ← resource.use(initialRunState.nestedContext) { engine.runSteps(nested, _) }
      (results, (resourcedState, resourcedRes)) = resTuple
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
    } yield {
      val completeSession = resourcedState.session.combine(results)
      (initialRunState.withSession(completeSession).appendLogs(fullLogs).prependCleanupStepsFrom(initialRunState), xor)
    }
  }
}
