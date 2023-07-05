package com.github.agourlay.cornichon.steps.wrapped

import cats.data.StateT
import cats.effect.IO
import com.github.agourlay.cornichon.core.Done.rightDone
import com.github.agourlay.cornichon.core.{ FailureLogInstruction, Resource, ScenarioRunner, Step, StepState, SuccessLogInstruction, WrapperStep }

// The `Resource` is created before `nested` and released after
case class WithResourceStep(nested: List[Step], resource: Resource) extends WrapperStep {
  val title = s"With resource block `${resource.title}`"

  override val stateUpdate: StepState = StateT { runState =>
    val initialDepth = runState.depth
    val resourceNested = runState.nestedContext
    ScenarioRunner.runStepsShortCircuiting(resource.acquire :: Nil, resourceNested).flatMap { resTuple =>
      val (acquireState, acquireRes) = resTuple
      acquireRes match {
        case Left(_) =>
          val logs = FailureLogInstruction("With resource block failed due to acquire step", initialDepth) +: acquireState.logStack :+ failedTitleLog(initialDepth)
          IO.pure((runState.mergeNested(acquireState, logs), acquireRes))
        case Right(_) =>
          val nestedNested = acquireState.nestedContext
          ScenarioRunner.runStepsShortCircuiting(nested, nestedNested).flatMap { resTuple =>
            val (nestedState, nestedRes) = resTuple
            // always trigger resource release
            ScenarioRunner.runStepsShortCircuiting(resource.release :: Nil, nestedState.upperLevelContext).flatMap { resTuple =>
              val (releaseState, releaseRes) = resTuple
              // gather all logs
              val innerLogs = releaseState.logStack ::: nestedState.logStack ::: acquireState.logStack
              val (report, logs) = (nestedRes, releaseRes) match {
                case (Left(_), _) =>
                  (nestedRes, FailureLogInstruction("With resource block failed", initialDepth) +: innerLogs :+ failedTitleLog(initialDepth))
                case (_, Left(_)) =>
                  (releaseRes, FailureLogInstruction("With resource block failed due to release step", initialDepth) +: innerLogs :+ failedTitleLog(initialDepth))
                case _ =>
                  (rightDone, SuccessLogInstruction("With resource block succeeded", initialDepth) +: innerLogs :+ successTitleLog(initialDepth))
              }
              // propagate potential cleanup steps
              val mergedState = runState.recordLogStack(logs)
                .registerCleanupSteps(nestedState.cleanupSteps)
                .registerCleanupSteps(acquireState.cleanupSteps)
                .registerCleanupSteps(releaseState.cleanupSteps)
              IO.pure((mergedState, report))
            }
          }
      }
    }
  }
}
