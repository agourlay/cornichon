package com.github.agourlay.cornichon.steps.check

import cats.data.StateT
import cats.effect.IO
import com.github.agourlay.cornichon.core.Done.rightDone
import com.github.agourlay.cornichon.core.{ Generator, _ }

case class ForAllStep[A, B, C, D, E, F](description: String, maxNumberOfRuns: Int)(ga: RandomContext => Generator[A], gb: RandomContext => Generator[B], gc: RandomContext => Generator[C], gd: RandomContext => Generator[D], ge: RandomContext => Generator[E], gf: RandomContext => Generator[F])(f: A => B => C => D => E => F => Step) extends WrapperStep {

  val baseTitle = s"ForAll values of generators check '$description'"
  val title = s"$baseTitle with maxNumberOfRuns=$maxNumberOfRuns"

  override val stateUpdate: StepState = StateT { runState =>
    val randomContext = runState.randomContext
    val genA = ga(randomContext)
    val genB = gb(randomContext)
    val genC = gc(randomContext)
    val genD = gd(randomContext)
    val genE = ge(randomContext)
    val genF = gf(randomContext)

    def repeatEvaluationOnSuccess(runNumber: Int)(runState: RunState): IO[(RunState, Either[FailedStep, Done])] =
      if (runNumber > maxNumberOfRuns)
        IO.pure((runState, rightDone))
      else {
        val s = runState.session
        val generatedA = genA.value(s)()
        val generatedB = genB.value(s)()
        val generatedC = genC.value(s)()
        val generatedD = genD.value(s)()
        val generatedE = genE.value(s)()
        val generatedF = genF.value(s)()

        val preRunLog = InfoLogInstruction(s"Run #$runNumber", runState.depth)
        val invariantRunState = runState.nestedContext.recordLog(preRunLog)
        val invariantStep = f(generatedA)(generatedB)(generatedC)(generatedD)(generatedE)(generatedF)

        invariantStep.runStep(invariantRunState).flatMap {
          case (newState, l @ Left(_)) =>
            val postRunLog = InfoLogInstruction(s"Run #$runNumber - Failed", runState.depth)
            val failedState = runState.mergeNested(newState).recordLog(postRunLog)
            IO.pure((failedState, l))
          case (newState, _) =>
            val postRunLog = InfoLogInstruction(s"Run #$runNumber", runState.depth)
            // success case we are not propagating the Session so runs do not interfere with each-others
            val nextRunState = runState.recordLogStack(newState.logStack).recordLog(postRunLog).registerCleanupSteps(newState.cleanupSteps)
            repeatEvaluationOnSuccess(runNumber + 1)(nextRunState)
        }
      }

    repeatEvaluationOnSuccess(1)(runState.nestedContext)
      .timed
      .map {
        case (executionTime, (checkState, res)) =>
          val depth = runState.depth
          val exec = Some(executionTime)
          val fullLogs = res match {
            case Left(_) =>
              FailureLogInstruction(s"$baseTitle block failed ", depth, exec) +: checkState.logStack :+ failedTitleLog(depth)
            case _ =>
              SuccessLogInstruction(s"$baseTitle block succeeded", depth, exec) +: checkState.logStack :+ successTitleLog(depth)
          }
          (runState.mergeNested(checkState, fullLogs), res)
      }
  }
}
