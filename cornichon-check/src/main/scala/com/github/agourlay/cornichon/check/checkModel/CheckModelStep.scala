package com.github.agourlay.cornichon.check.checkModel

import cats.data.Validated.Invalid
import cats.data.ValidatedNel
import cats.syntax.option._
import cats.syntax.either._
import cats.syntax.validated._
import cats.syntax.apply._
import com.github.agourlay.cornichon.check.RandomContext
import com.github.agourlay.cornichon.core.Done.rightDone
import com.github.agourlay.cornichon.core._
import monix.eval.Task
import com.github.agourlay.cornichon.util.Timing._

import scala.util.Random

case class CheckModelStep[A, B, C, D, E, F](
    maxNumberOfRuns: Int,
    maxNumberOfTransitions: Int,
    modelRunner: ModelRunner[A, B, C, D, E, F],
    withSeed: Option[Long]) extends WrapperStep {

  private val initialSeed = withSeed.getOrElse(System.currentTimeMillis())
  private val randomContext = RandomContext(new Random(new java.util.Random(initialSeed)))

  private val genA = modelRunner.generatorA(randomContext)
  private val genB = modelRunner.generatorB(randomContext)
  private val genC = modelRunner.generatorC(randomContext)
  private val genD = modelRunner.generatorD(randomContext)
  private val genE = modelRunner.generatorE(randomContext)
  private val genF = modelRunner.generatorF(randomContext)

  private val model = modelRunner.model

  private val checkEngine = new CheckModelEngine(this, model, maxNumberOfTransitions, randomContext.seededRandom, genA, genB, genC, genD, genE, genF)

  val title = s"Checking model '${model.description}' with maxNumberOfRuns=$maxNumberOfRuns and maxNumberOfTransitions=$maxNumberOfTransitions and seed=$initialSeed"

  private def repeatModelOnSuccess(runNumber: Int)(engine: Engine, initialRunState: RunState): Task[(RunState, Either[FailedStep, Done])] =
    if (runNumber > maxNumberOfRuns)
      Task.now((initialRunState, rightDone))
    else {
      val preRunLog = InfoLogInstruction(s"Run #$runNumber", initialRunState.depth)
      val checkEngineRunState = initialRunState.nestedContext.recordLog(preRunLog)
      checkEngine.run(engine, checkEngineRunState).flatMap {
        case (newState, Left(fs)) ⇒
          val postRunLog = InfoLogInstruction(s"Run #$runNumber - Failed", initialRunState.depth)
          val failedState = initialRunState.mergeNested(newState).recordLog(postRunLog)
          Task.now((failedState, fs.asLeft))
        case (newState, Right(endOfRun)) ⇒
          // success case we are mot propagating the Session so runs do not interfere with each-others
          val nextRunState = initialRunState.recordLogStack(newState.logStack).registerCleanupSteps(newState.cleanupSteps)
          val postRunLog = buildInfoRunLog(runNumber, endOfRun, initialRunState.depth)
          repeatModelOnSuccess(runNumber + 1)(engine, nextRunState.recordLog(postRunLog))
      }
    }

  private def buildInfoRunLog(runNumber: Int, endOfRun: SuccessEndOfRun, depth: Int): LogInstruction = {
    val reason = endOfRun match {
      case EndPropertyReached(property, numberOfTransitions) ⇒
        s"End reached on property '$property' after $numberOfTransitions transitions"
      case MaxTransitionReached(_) ⇒
        "Max transitions number per run reached"
    }
    InfoLogInstruction(s"Run #$runNumber - $reason", depth)
  }

  private def validateTransitions(transitions: Map[PropertyN[A, B, C, D, E, F], List[(Double, PropertyN[A, B, C, D, E, F])]]): ValidatedNel[CornichonError, Done] = {
    val emptyTransitionForState: ValidatedNel[CornichonError, Done] = transitions.find(_._2.isEmpty)
      .map(s ⇒ EmptyTransitionsDefinitionForProperty(s._1.description)).toInvalidNel(Done)

    val noTransitionsForStart: ValidatedNel[CornichonError, Done] = if (transitions.get(model.entryPoint).isEmpty)
      NoTransitionsDefinitionForStartingProperty(model.entryPoint.description).invalidNel
    else Done.validDone

    val duplicateEntries: ValidatedNel[CornichonError, Done] = transitions.find { e ⇒
      val allProperties = e._2.map(_._2)
      allProperties.distinct.size != allProperties.size
    }.map(_._1.description).map(DuplicateTransitionsDefinitionForProperty).toInvalidNel(Done)

    val sumOfWeightIsCorrect: ValidatedNel[CornichonError, Done] = transitions.find { e ⇒
      e._2.map(_._1).sum != 1.0d
    }.map(_._1.description).map(IncorrectTransitionsWeightDefinitionForProperty).toInvalidNel(Done)

    emptyTransitionForState *> noTransitionsForStart *> duplicateEntries *> sumOfWeightIsCorrect
  }

  override def run(engine: Engine)(initialRunState: RunState): Task[(RunState, FailedStep Either Done)] =
    withDuration {
      validateTransitions(model.transitions) match {
        case Invalid(ce) ⇒
          Task.now((initialRunState, FailedStep(this, ce).asLeft))
        case _ ⇒
          repeatModelOnSuccess(runNumber = 1)(engine, initialRunState.nestedContext)
      }
    }.map {
      case (run, executionTime) ⇒
        val depth = initialRunState.depth
        val (checkState, res) = run
        val fullLogs = res match {
          case Left(_) ⇒
            FailureLogInstruction(s"Check model block failed ", depth, Some(executionTime)) +: checkState.logStack :+ failedTitleLog(depth)

          case _ ⇒
            SuccessLogInstruction(s"Check block succeeded", depth, Some(executionTime)) +: checkState.logStack :+ successTitleLog(depth)
        }
        (initialRunState.mergeNested(checkState, fullLogs), res)
    }
}

case class EmptyTransitionsDefinitionForProperty(description: String) extends CornichonError {
  def baseErrorMessage: String = s"Empty outgoing transitions definition found '$description'"
}

case class DuplicateTransitionsDefinitionForProperty(description: String) extends CornichonError {
  def baseErrorMessage: String = s"Transitions definition from '$description' contains duplicates target properties"
}

case class IncorrectTransitionsWeightDefinitionForProperty(description: String) extends CornichonError {
  def baseErrorMessage: String = s"Transitions definition from '$description' contains incorrect weight definition (above 1.0)"
}

case class NoTransitionsDefinitionForStartingProperty(description: String) extends CornichonError {
  def baseErrorMessage: String = s"No outgoing transitions definition found for starting property '$description'"
}

case class InvalidTransitionDefinitionForProperty(description: String) extends CornichonError {
  def baseErrorMessage: String = s"Invalid transition definition for property '$description'"
}
