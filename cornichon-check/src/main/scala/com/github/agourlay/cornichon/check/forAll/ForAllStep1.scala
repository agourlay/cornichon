package com.github.agourlay.cornichon.check.forAll

import com.github.agourlay.cornichon.check.{ Generator, RandomContext }
import com.github.agourlay.cornichon.core.Done.rightDone
import com.github.agourlay.cornichon.core.core.StepResult
import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.util.Timing._
import monix.eval.Task

import scala.util.Random

class ForAllStep1[A](description: String, maxNumberOfRuns: Int, withSeed: Option[Long] = None)(ga: RandomContext ⇒ Generator[A], f: A ⇒ Step) extends WrapperStep {

  private val randomContext = {
    val seed = withSeed.getOrElse(System.currentTimeMillis())
    val rd = new Random(new java.util.Random(seed))
    RandomContext(seed, rd)
  }

  val genA = ga(randomContext)
  val baseTitle = s"ForAll '${genA.name}' check '$description'"
  val title = s"$baseTitle with maxNumberOfRuns=$maxNumberOfRuns and seed=${randomContext.seed}"

  private def repeatModelOnSuccess(runNumber: Int)(engine: Engine, initialRunState: RunState): Task[(RunState, Either[FailedStep, Done])] =
    if (runNumber > maxNumberOfRuns)
      Task.now((initialRunState, rightDone))
    else {
      val generated = genA.value(initialRunState.session)()
      val preRunLog = InfoLogInstruction(s"Run #$runNumber [${genA.name} -> $generated]", initialRunState.depth)
      val invariantRunState = initialRunState.nestedContext.appendLog(preRunLog)
      val invariantStep = f(generated)
      invariantStep.run(engine)(invariantRunState).flatMap {
        case (newState, l @ Left(_)) ⇒
          val postRunLog = InfoLogInstruction(s"Run #$runNumber - Failed", initialRunState.depth)
          val failedState = initialRunState.mergeNested(newState).appendLog(postRunLog)
          Task.now((failedState, l))
        case (newState, Right(_)) ⇒
          // success case we are mot propagating the Session so runs do not interfere with each-others
          val nextRunState = initialRunState.appendLogsFrom(newState).prependCleanupStepsFrom(newState)
          val postRunLog = InfoLogInstruction(s"Run #$runNumber", initialRunState.depth)
          repeatModelOnSuccess(runNumber + 1)(engine, nextRunState.appendLog(postRunLog))
      }
    }

  def run(engine: Engine)(initialRunState: RunState): StepResult =
    withDuration {
      repeatModelOnSuccess(0)(engine, initialRunState.nestedContext)
    }.map {
      case (run, executionTime) ⇒
        val depth = initialRunState.depth
        val (checkState, res) = run
        val fullLogs = res match {
          case Left(_) ⇒
            failedTitleLog(depth) +: checkState.logs :+ FailureLogInstruction(s"$baseTitle block failed ", depth, Some(executionTime))

          case _ ⇒
            successTitleLog(depth) +: checkState.logs :+ SuccessLogInstruction(s"$baseTitle block succeeded", depth, Some(executionTime))
        }
        (initialRunState.mergeNested(checkState, fullLogs), res)
    }
}
