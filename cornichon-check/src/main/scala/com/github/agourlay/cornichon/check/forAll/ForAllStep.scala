package com.github.agourlay.cornichon.check.forAll

import com.github.agourlay.cornichon.check.{ Generator, NoValueGenerator, RandomContext }
import com.github.agourlay.cornichon.core.Done.rightDone
import com.github.agourlay.cornichon.core.core.StepResult
import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.util.Timing._
import monix.eval.Task

import scala.util.Random

class ForAllStep[A, B, C, D, E, F](description: String, maxNumberOfRuns: Int, withSeed: Option[Long] = None)(ga: RandomContext ⇒ Generator[A], gb: RandomContext ⇒ Generator[B], gc: RandomContext ⇒ Generator[C], gd: RandomContext ⇒ Generator[D], ge: RandomContext ⇒ Generator[E], gf: RandomContext ⇒ Generator[F])(f: A ⇒ B ⇒ C ⇒ D ⇒ E ⇒ F ⇒ Step) extends WrapperStep {

  private val randomContext = {
    val seed = withSeed.getOrElse(System.currentTimeMillis())
    val rd = new Random(new java.util.Random(seed))
    RandomContext(seed, rd)
  }

  val genA = ga(randomContext)
  val genB = gb(randomContext)
  val genC = gc(randomContext)
  val genD = gd(randomContext)
  val genE = ge(randomContext)
  val genF = gf(randomContext)

  val concreteGens = List(genA, genB, genC, genD, genE, genF).filter(_ != NoValueGenerator)

  val baseTitle = s"ForAll '${concreteGens.map(_.name).mkString(",")}' check '$description'"
  val title = s"$baseTitle with maxNumberOfRuns=$maxNumberOfRuns and seed=${randomContext.seed}"

  private def repeatModelOnSuccess(runNumber: Int)(engine: Engine, initialRunState: RunState): Task[(RunState, Either[FailedStep, Done])] =
    if (runNumber > maxNumberOfRuns)
      Task.now((initialRunState, rightDone))
    else {
      val generatedA = genA.value(initialRunState.session)()
      val generatedB = genB.value(initialRunState.session)()
      val generatedC = genC.value(initialRunState.session)()
      val generatedD = genD.value(initialRunState.session)()
      val generatedE = genE.value(initialRunState.session)()
      val generatedF = genF.value(initialRunState.session)()

      val preRunLog = InfoLogInstruction(s"Run #$runNumber", initialRunState.depth)
      val invariantRunState = initialRunState.nestedContext.appendLog(preRunLog)
      val invariantStep = f(generatedA)(generatedB)(generatedC)(generatedD)(generatedE)(generatedF)

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
