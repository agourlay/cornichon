package com.github.agourlay.cornichon.check

import cats.data.Validated.Invalid
import cats.data.ValidatedNel
import cats.syntax.option._
import cats.syntax.either._
import cats.syntax.validated._
import cats.syntax.apply._
import com.github.agourlay.cornichon.core.Done.rightDone
import com.github.agourlay.cornichon.core._
import monix.eval.Task
import com.github.agourlay.cornichon.util.Timing._

import scala.util.Random

case class CheckStep[A, B, C, D, E, F](
    maxNumberOfRuns: Int,
    maxNumberOfTransitions: Int,
    modelRunner: ModelRunner[A, B, C, D, E, F],
    withSeed: Option[Long]) extends WrapperStep {

  private val randomContext = {
    val seed = withSeed.getOrElse(System.currentTimeMillis())
    val rd = new Random(new java.util.Random(seed))
    RandomContext(seed, rd)
  }

  private val genA = modelRunner.generatorA(randomContext)
  private val genB = modelRunner.generatorB(randomContext)
  private val genC = modelRunner.generatorC(randomContext)
  private val genD = modelRunner.generatorD(randomContext)
  private val genE = modelRunner.generatorE(randomContext)
  private val genF = modelRunner.generatorF(randomContext)

  private val model = modelRunner.model

  private val checkEngine = new CheckEngine(this, model, maxNumberOfTransitions, randomContext.seededRandom, genA, genB, genC, genD, genE, genF)

  val title = s"Checking model '${model.description}' with maxNumberOfRuns=$maxNumberOfRuns and maxNumberOfTransitions=$maxNumberOfTransitions and seed=${randomContext.seed}"

  private def repeatSuccessModel(runNumber: Int)(engine: Engine, initialRunState: RunState): Task[(RunState, Either[FailedStep, Done])] =
    if (runNumber > maxNumberOfRuns)
      Task.now((initialRunState, rightDone))
    else {
      val preRunLog = InfoLogInstruction(s"Run #$runNumber", initialRunState.depth)
      val checkEngineRunState = initialRunState.nestedContext.appendLog(preRunLog)
      checkEngine.run(engine, checkEngineRunState).flatMap {
        case (newState, res) ⇒
          res match {
            case Left(fs) ⇒
              val postRunLog = InfoLogInstruction(s"Run #$runNumber - Failed", initialRunState.depth)
              val failedState = initialRunState.mergeNested(newState).appendLog(postRunLog)
              Task.now((failedState, fs.asLeft))
            case Right(endOfRun) ⇒
              // success case we are mot propagating the Session so runs do not interfere with each-others
              val nextRunState = initialRunState.appendLogsFrom(newState).prependCleanupStepsFrom(newState)
              val postRunLog = buildInfoRunLog(runNumber, endOfRun, initialRunState.depth)
              repeatSuccessModel(runNumber + 1)(engine, nextRunState.appendLog(postRunLog))
          }
      }
    }

  private def buildInfoRunLog(runNumber: Int, endOfRun: SuccessEndOfRun, depth: Int): LogInstruction = {
    val reason = endOfRun match {
      case EndActionReached(action, numberOfTransitions) ⇒
        s"End reached on action '$action' after $numberOfTransitions transitions"
      case MaxTransitionReached(_) ⇒
        "Max transitions number per run reached"
    }
    InfoLogInstruction(s"Run #$runNumber - $reason", depth)
  }

  private def validateTransitions(transitions: Map[ActionN[A, B, C, D, E, F], List[(Double, ActionN[A, B, C, D, E, F])]]): ValidatedNel[CornichonError, Done] = {
    val emptyTransitionForState: ValidatedNel[CornichonError, Done] = transitions.find(_._2.isEmpty)
      .map(s ⇒ EmptyTransitionsDefinitionForAction(s._1.description)).toInvalidNel(Done)

    val noTransitionsForStart: ValidatedNel[CornichonError, Done] = if (transitions.get(model.startingAction).isEmpty)
      NoTransitionsDefinitionForStartingAction(model.startingAction.description).invalidNel
    else Done.validDone

    val duplicateEntries: ValidatedNel[CornichonError, Done] = transitions.find { e ⇒
      val allStates = e._2.map(_._2)
      allStates.distinct.size != allStates.size
    }.map(_._1.description).map(DuplicateTransitionsDefinitionForState).toInvalidNel(Done)

    val sumOfWeightIsCorrect: ValidatedNel[CornichonError, Done] = transitions.find { e ⇒
      e._2.map(_._1).sum != 1.0d
    }.map(_._1.description).map(IncorrectTransitionsWeightDefinitionForAction).toInvalidNel(Done)

    emptyTransitionForState *> noTransitionsForStart *> duplicateEntries *> sumOfWeightIsCorrect
  }

  override def run(engine: Engine)(initialRunState: RunState): Task[(RunState, FailedStep Either Done)] =
    withDuration {
      validateTransitions(model.transitions) match {
        case Invalid(ce) ⇒
          Task.delay((initialRunState, FailedStep(this, ce).asLeft))
        case _ ⇒
          repeatSuccessModel(1)(engine: Engine, initialRunState.nestedContext)
      }
    }.map {
      case (run, executionTime) ⇒
        val (checkState, report) = run
        val depth = initialRunState.depth
        val (fullLogs, xor) = report.fold(
          failedStep ⇒ {
            val fullLogs = failedTitleLog(depth) +: checkState.logs :+ FailureLogInstruction(s"Check model block failed ", depth, Some(executionTime))
            (fullLogs, Left(failedStep))
          },
          _ ⇒ {
            val fullLogs = successTitleLog(depth) +: checkState.logs :+ SuccessLogInstruction(s"Check block succeeded", depth, Some(executionTime))
            (fullLogs, rightDone)
          }
        )
        (initialRunState.mergeNested(checkState, fullLogs), xor)
    }
}

case class EmptyTransitionsDefinitionForAction(actionDescription: String) extends CornichonError {
  def baseErrorMessage: String = s"Empty outgoing transitions definition found '$actionDescription'"
}

case class DuplicateTransitionsDefinitionForState(actionDescription: String) extends CornichonError {
  def baseErrorMessage: String = s"Transitions definition from '$actionDescription' contains duplicates target action"
}

case class IncorrectTransitionsWeightDefinitionForAction(actionDescription: String) extends CornichonError {
  def baseErrorMessage: String = s"Transitions definition from '$actionDescription' contains incorrect weight definition (above 1.0)"
}

case class NoTransitionsDefinitionForStartingAction(actionDescription: String) extends CornichonError {
  def baseErrorMessage: String = s"No outgoing transitions definition found for starting action '$actionDescription'"
}

case class InvalidTransitionDefinitionForAction(actionDescription: String) extends CornichonError {
  def baseErrorMessage: String = s"Invalid transition definition for action '$actionDescription'"
}
