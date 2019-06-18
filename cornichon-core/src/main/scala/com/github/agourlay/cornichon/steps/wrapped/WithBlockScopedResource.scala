package com.github.agourlay.cornichon.steps.wrapped

import cats.data.StateT
import cats.syntax.monoid._
import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.core.core.StepState
import com.github.agourlay.cornichon.dsl.BlockScopedResource

case class WithBlockScopedResource(nested: List[Step], resource: BlockScopedResource) extends WrapperStep {

  val title = resource.openingTitle

  override def onEngine(engine: Engine): StepState = StateT { initialRunState ⇒
    resource.use(initialRunState.nestedContext)(engine.runStepsShortCircuiting(nested, _)).map { resTuple ⇒
      val (results, (resourcedState, resourcedRes)) = resTuple
      val initialDepth = initialRunState.depth
      val closingTitle = resource.closingTitle
      val logStack = resourcedRes match {
        case Left(_) ⇒ FailureLogInstruction(closingTitle, initialDepth) +: resourcedState.logStack :+ failedTitleLog(initialDepth)
        case _       ⇒ SuccessLogInstruction(closingTitle, initialDepth) +: resourcedState.logStack :+ successTitleLog(initialDepth)
      }
      val completeSession = resourcedState.session.combine(results)
      // Manual nested merge
      (initialRunState.withSession(completeSession).recordLogStack(logStack).registerCleanupSteps(initialRunState.cleanupSteps), resourcedRes)
    }
  }
}
