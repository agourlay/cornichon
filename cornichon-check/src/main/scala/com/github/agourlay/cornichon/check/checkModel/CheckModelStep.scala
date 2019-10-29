package com.github.agourlay.cornichon.check.checkModel

import cats.data.Validated.Invalid
import cats.data.{ StateT, ValidatedNel }
import cats.syntax.option._
import cats.syntax.either._
import cats.syntax.validated._
import cats.syntax.apply._
import com.github.agourlay.cornichon.core.Done.rightDone
import com.github.agourlay.cornichon.core._
import monix.eval.Task

case class CheckModelStep[A, B, C, D, E, F](
    maxNumberOfRuns: Int,
    maxNumberOfTransitions: Int,
    modelRunner: ModelRunner[A, B, C, D, E, F]) extends WrapperStep {

  private val model = modelRunner.model

  val title = s"Checking model '${model.description}' with maxNumberOfRuns=$maxNumberOfRuns and maxNumberOfTransitions=$maxNumberOfTransitions"

  private def repeatModelOnSuccess(checkEngine: CheckModelEngine[A, B, C, D, E, F], runNumber: Int)(runState: RunState): Task[(RunState, Either[FailedStep, Done])] =
    if (runNumber > maxNumberOfRuns)
      Task.now((runState, rightDone))
    else {
      val preRunLog = InfoLogInstruction(s"Run #$runNumber", runState.depth)
      val checkEngineRunState = runState.nestedContext.recordLog(preRunLog)
      checkEngine.run(checkEngineRunState).flatMap {
        case (newState, Left(fs)) =>
          val postRunLog = InfoLogInstruction(s"Run #$runNumber - Failed", runState.depth)
          val failedState = runState.mergeNested(newState).recordLog(postRunLog)
          Task.now((failedState, fs.asLeft))
        case (newState, Right(endOfRun)) =>
          // success case we are mot propagating the Session so runs do not interfere with each-others
          val nextRunState = runState.recordLogStack(newState.logStack).registerCleanupSteps(newState.cleanupSteps)
          val postRunLog = buildInfoRunLog(runNumber, endOfRun, runState.depth)
          repeatModelOnSuccess(checkEngine, runNumber + 1)(nextRunState.recordLog(postRunLog))
      }
    }

  private def buildInfoRunLog(runNumber: Int, endOfRun: SuccessEndOfRun, depth: Int): LogInstruction = {
    val reason = endOfRun match {
      case EndPropertyReached(property, numberOfTransitions) =>
        s"End reached on property '$property' after $numberOfTransitions transitions"
      case MaxTransitionReached(numberOfTransitions) =>
        s"Max number of transitions per run reached ($numberOfTransitions)"
    }
    InfoLogInstruction(s"Run #$runNumber - $reason", depth)
  }

  private def validateTransitions(transitions: Map[PropertyN[A, B, C, D, E, F], List[(Int, PropertyN[A, B, C, D, E, F])]]): ValidatedNel[CornichonError, Done] = {
    val emptyTransitionForState: ValidatedNel[CornichonError, Done] = transitions.find(_._2.isEmpty)
      .map(s => EmptyTransitionsDefinitionForProperty(s._1.description)).toInvalidNel(Done)

    val noTransitionsForStart: ValidatedNel[CornichonError, Done] = if (transitions.get(model.entryPoint).isEmpty)
      NoTransitionsDefinitionForStartingProperty(model.entryPoint.description).invalidNel
    else Done.validDone

    val duplicateEntries: ValidatedNel[CornichonError, Done] = transitions.find { e =>
      val allProperties = e._2.map(_._2)
      allProperties.distinct.size != allProperties.size
    }.map(_._1.description).map(DuplicateTransitionsDefinitionForProperty).toInvalidNel(Done)

    val sumOfWeightIsCorrect: ValidatedNel[CornichonError, Done] = transitions.find { e =>
      e._2.map(_._1).sum != 100
    }.map(_._1.description).map(IncorrectTransitionsWeightDefinitionForProperty).toInvalidNel(Done)

    emptyTransitionForState *> noTransitionsForStart *> duplicateEntries *> sumOfWeightIsCorrect
  }

  private def checkModel(runState: RunState): Task[(RunState, Either[FailedStep, Done])] =
    validateTransitions(model.transitions) match {
      case Invalid(ce) =>
        Task.now((runState, FailedStep(this, ce).asLeft))
      case _ =>
        val randomContext = runState.randomContext
        val genA = modelRunner.generatorA(randomContext)
        val genB = modelRunner.generatorB(randomContext)
        val genC = modelRunner.generatorC(randomContext)
        val genD = modelRunner.generatorD(randomContext)
        val genE = modelRunner.generatorE(randomContext)
        val genF = modelRunner.generatorF(randomContext)
        val checkEngine = new CheckModelEngine(this, model, maxNumberOfTransitions, randomContext.seededRandom, genA, genB, genC, genD, genE, genF)
        repeatModelOnSuccess(checkEngine, runNumber = 1)(runState.nestedContext)
    }

  override val stateUpdate: StepState = StateT { runState =>
    checkModel(runState)
      .timed
      .map {
        case (executionTime, run) =>
          val depth = runState.depth
          val (checkState, res) = run
          val fullLogs = res match {
            case Left(_) =>
              FailureLogInstruction(s"Check model block failed ", depth, Some(executionTime)) +: checkState.logStack :+ failedTitleLog(depth)
            case _ =>
              SuccessLogInstruction(s"Check model block succeeded", depth, Some(executionTime)) +: checkState.logStack :+ successTitleLog(depth)
          }
          (runState.mergeNested(checkState, fullLogs), res)
      }
  }
}

case class EmptyTransitionsDefinitionForProperty(description: String) extends CornichonError {
  def baseErrorMessage: String = s"Empty outgoing transitions definition found '$description'"
}

case class DuplicateTransitionsDefinitionForProperty(description: String) extends CornichonError {
  def baseErrorMessage: String = s"Transitions definition from '$description' contains duplicates target properties"
}

case class IncorrectTransitionsWeightDefinitionForProperty(description: String) extends CornichonError {
  def baseErrorMessage: String = s"Transitions definition from '$description' contains incorrect weight definition (above 100)"
}

case class NoTransitionsDefinitionForStartingProperty(description: String) extends CornichonError {
  def baseErrorMessage: String = s"No outgoing transitions definition found for starting property '$description'"
}

case class InvalidTransitionDefinitionForProperty(description: String) extends CornichonError {
  def baseErrorMessage: String = s"Invalid transition definition for property '$description'"
}
