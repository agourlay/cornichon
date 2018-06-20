package com.github.agourlay.cornichon.check

import cats.data.Validated.Invalid
import cats.data.{ NonEmptyList, ValidatedNel }
import cats.syntax.option._
import cats.syntax.either._
import cats.syntax.validated._
import cats.syntax.apply._
import com.github.agourlay.cornichon.core.Done.rightDone
import com.github.agourlay.cornichon.core._
import monix.eval.Task
import com.github.agourlay.cornichon.util.Timing._

case class CheckStep[A, B, C, D, E, F](maxNumberOfRuns: Int, maxNumberOfTransitions: Int, modelRunner: ModelRunner[A, B, C, D, E, F]) extends WrapperStep {

  val model = modelRunner.model
  val title = s"Checking model '${model.description}' with maxNumberOfRuns=$maxNumberOfRuns amd maxNumberOfTransitions=$maxNumberOfTransitions"

  private def repeatSuccessModel(runNumber: Int)(engine: Engine, initialRunState: RunState): Task[(RunState, Either[FailedStep, Done])] =
    if (runNumber > maxNumberOfRuns)
      Task.now((initialRunState, rightDone))
    else {
      val checkEngine = new CheckEngine(engine, this, modelRunner, maxNumberOfTransitions)
      checkEngine.run(initialRunState).flatMap {
        case (newState, res) ⇒
          res match {
            case Left(fs) ⇒
              Task.now((newState, fs.asLeft))
            case Right(endOfRun) ⇒
              // success case we are propagating the logs but the not Session itself
              val nextRunState = newState.withSession(initialRunState.session)
              val runLog = buildInfoRunLog(runNumber, endOfRun, initialRunState.depth)
              repeatSuccessModel(runNumber + 1)(engine, nextRunState.appendLog(runLog))
          }
      }
    }

  private def buildInfoRunLog(runNumber: Int, endOfRun: SuccessEndOfRun, depth: Int): LogInstruction = {
    val reason = endOfRun match {
      case EndStateReached(state, numberOfTransitions) ⇒ s"End state reached on state $state after $numberOfTransitions transitions"
      case MaxTransitionReached(_)                     ⇒ "Max transitions number per run reached"
    }
    InfoLogInstruction(s"Run #$runNumber - $reason", depth)
  }

  private def validateTransitions(transitions: Map[State[A, B, C, D, E, F], List[(Double, State[A, B, C, D, E, F])]]): ValidatedNel[CornichonError, Done] = {
    val emptyTransitionForState: ValidatedNel[CornichonError, Done] = transitions.find(_._2.isEmpty)
      .map(s ⇒ EmptyTransitionsDefinitionForState(s._1.description)).toInvalidNel(Done)

    val noTransitionsForStart: ValidatedNel[CornichonError, Done] = if (transitions.get(model.startingState).isEmpty)
      NoTransitionsDefinitionForStartingState(model.startingState.description).invalidNel
    else Done.validDone

    val duplicateEntries: ValidatedNel[CornichonError, Done] = transitions.find { e ⇒
      val allStates = e._2.map(_._2)
      allStates.distinct.size != allStates.size
    }.map(_._1.description).map(DuplicateTransitionsDefinitionForState).toInvalidNel(Done)

    val sumOfWeightIsCorrect: ValidatedNel[CornichonError, Done] = transitions.find { e ⇒
      e._2.map(_._1).sum != 1.0d
    }.map(_._1.description).map(IncorrectTransitionsWeightDefinitionForState).toInvalidNel(Done)

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
            val artificialFailedStep = FailedStep.fromSingle(failedStep.step, CheckBlockContainFailedSteps(failedStep.errors))
            (fullLogs, Left(artificialFailedStep))
          },
          _ ⇒ {
            val fullLogs = successTitleLog(depth) +: checkState.logs :+ SuccessLogInstruction(s"Check block succeeded", depth, Some(executionTime))
            (fullLogs, rightDone)
          }
        )
        (initialRunState.mergeNested(checkState, fullLogs), xor)
    }
}

case class EmptyTransitionsDefinitionForState(stateDescription: String) extends CornichonError {
  def baseErrorMessage: String = s"Empty outgoing transitions definition found '$stateDescription'"
}

case class DuplicateTransitionsDefinitionForState(stateDescription: String) extends CornichonError {
  def baseErrorMessage: String = s"Transitions definition from '$stateDescription' contains duplicates target state"
}

case class IncorrectTransitionsWeightDefinitionForState(stateDescription: String) extends CornichonError {
  def baseErrorMessage: String = s"Transitions definition from '$stateDescription' contains incorrect weight definition (above 1.0)"
}

case class NoTransitionsDefinitionForStartingState(stateDescription: String) extends CornichonError {
  def baseErrorMessage: String = s"No outgoing transitions definition found for starting state $stateDescription"
}

case class InvalidTransitionDefinitionForState(stateDescription: String) extends CornichonError {
  def baseErrorMessage: String = s"Invalid transition definition for state '$stateDescription'"
}

case class CheckBlockContainFailedSteps(errors: NonEmptyList[CornichonError]) extends CornichonError {
  val baseErrorMessage = s"Check block failed"
  override val causedBy = errors.toList
}
