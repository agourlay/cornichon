package com.github.agourlay.cornichon.check

import java.util.concurrent.ConcurrentLinkedQueue

import cats.data.NonEmptyList
import com.github.agourlay.cornichon.core._
import monix.eval.Task
import cats.syntax.either._
import com.github.agourlay.cornichon.util.Printing

import collection.JavaConverters._
import scala.util.Random

class CheckEngine[A, B, C, D, E, F](
    cs: CheckStep[A, B, C, D, E, F],
    model: Model[A, B, C, D, E, F],
    maxNumberOfTransitions: Int,
    rd: Random,
    genA: Generator[A],
    genB: Generator[B],
    genC: Generator[C],
    genD: Generator[D],
    genE: Generator[E],
    genF: Generator[F]) {

  // Assertions do not propagate their Session!
  private def checkStepConditions(engine: Engine, initialRunState: RunState)(assertions: List[Step]): Task[List[Either[FailedStep, Done]]] =
    Task.gather(assertions.map(_.run(engine)(initialRunState).map(_._2)))

  private def validTransitions(engine: Engine, initialRunState: RunState)(transitions: List[(Double, ActionN[A, B, C, D, E, F])]): Task[List[(Double, ActionN[A, B, C, D, E, F], Boolean)]] =
    Task.gather {
      transitions.map {
        case (weight, actions) ⇒
          checkStepConditions(engine, initialRunState)(actions.preConditions).map { preConditionsRes ⇒
            val failedConditions = preConditionsRes.collect { case Left(e) ⇒ e }
            (weight, actions, failedConditions.isEmpty)
          }
      }
    }

  def run(engine: Engine, initialRunState: RunState): Task[(RunState, FailedStep Either SuccessEndOfRun)] =
    //check precondition for starting action
    checkStepConditions(engine, initialRunState)(model.startingAction.preConditions).flatMap { startingPreConditions ⇒
      val errors = startingPreConditions.collect { case Left(e) ⇒ e }
      NonEmptyList.fromList(errors) match {
        case None ⇒
          // run first state
          loopRun(engine, initialRunState, model.startingAction, 0)
        case Some(failures) ⇒
          val errors = failures.flatMap(_.errors)
          Task.now((initialRunState, FailedStep(cs, errors).asLeft))
      }
    }

  private def loopRun(engine: Engine, initialRunState: RunState, action: ActionN[A, B, C, D, E, F], currentNumberOfTransitions: Int): Task[(RunState, FailedStep Either SuccessEndOfRun)] =
    if (currentNumberOfTransitions > maxNumberOfTransitions)
      Task.now((initialRunState, MaxTransitionReached(maxNumberOfTransitions).asRight))
    else
      runActionAndValidatePostConditions(engine, initialRunState, action).flatMap {
        case (newState, Left(fs)) ⇒
          Task.now((newState, fs.asLeft))
        case (newState, Right(_)) ⇒
          // check available outgoing transitions
          model.transitions.get(action) match {
            case None ⇒
              // no transitions defined -> end of run
              Task.now((newState, EndActionReached(action.description, currentNumberOfTransitions).asRight))
            case Some(transitions) ⇒
              validTransitions(engine, newState)(transitions).flatMap { possibleNextStates ⇒
                val validNext = possibleNextStates.filter(_._3)
                if (validNext.isEmpty) {
                  val error = NoValidTransitionAvailableForState(action.description)
                  val noTransitionLog = FailureLogInstruction(error.baseErrorMessage, initialRunState.depth)
                  Task.now((newState.appendLog(noTransitionLog), FailedStep(cs, NonEmptyList.one(error)).asLeft))
                } else {
                  // pick one transition according to the weight
                  val nextAction = pickTransitionAccordingToProbability(rd, validNext)
                  loopRun(engine, newState, nextAction, currentNumberOfTransitions + 1)
                }
              }
          }
      }

  // Assumes valid pre-conditions
  private def runActionAndValidatePostConditions(engine: Engine, initialRunState: RunState, action: ActionN[A, B, C, D, E, F]): Task[(RunState, FailedStep Either Done)] = {
    val s = initialRunState.session
    // Init Gens
    val logQueue = new ConcurrentLinkedQueue[(String, String)]
    val ga = genA.valueWithLog(logQueue, s)
    val gb = genB.valueWithLog(logQueue, s)
    val gc = genC.valueWithLog(logQueue, s)
    val gd = genD.valueWithLog(logQueue, s)
    val ge = genE.valueWithLog(logQueue, s)
    val gf = genF.valueWithLog(logQueue, s)
    // Generate effect
    val effect = action.effectN(ga, gb, gc, gd, ge, gf)
    effect.run(engine)(initialRunState.resetLogs).flatMap {
      case (ns, res) ⇒
        // Generators are called inside the `Steps`, so we need to call them to generate the log
        val newState = injectActionLogWithGeneratedValue(logQueue, action.description, initialRunState.logs, ns)
        res match {
          case Left(_) ⇒
            Task.now(newState -> res)
          case Right(_) ⇒
            // check post-conditions
            checkStepConditions(engine, newState)(action.postConditions)
              .flatMap { afterConditionIsValid ⇒
                val failures = afterConditionIsValid.collect { case Left(e) ⇒ e }
                NonEmptyList.fromList(failures) match {
                  case None ⇒
                    Task.now(newState -> res)
                  case Some(errors) ⇒
                    val error = PostConditionBroken(action.description, errors.flatMap(_.errors))
                    val postConditionLog = FailureLogInstruction(error.renderedMessage, initialRunState.depth)
                    Task.now((newState.appendLog(postConditionLog), FailedStep(cs, NonEmptyList.one(error)).asLeft))
                }
              }
        }
    }
  }

  private def injectActionLogWithGeneratedValue(queue: ConcurrentLinkedQueue[(String, String)], actionDescription: String, previousLogs: Vector[LogInstruction], newState: RunState): RunState = {
    val valuesLabel = {
      if (queue.isEmpty) ""
      else {
        val generatedValues = queue.asScala.toList.sortBy(_._1)
        val generatedValuesLog = Printing.printArrowPairs(generatedValues)
        s" [$generatedValuesLog]"
      }
    }
    val actionNameLog = InfoLogInstruction(s"$actionDescription$valuesLabel", newState.depth)
    newState.prependLogs(previousLogs :+ actionNameLog)
  }

  //https://stackoverflow.com/questions/9330394/how-to-pick-an-item-by-its-probability
  private def pickTransitionAccordingToProbability[Z](rd: Random, inputs: List[(Double, Z, Boolean)]): Z = {
    val weight = rd.nextDouble()
    var cumulativeProbability = 0.0

    var selectedAction: Option[Z] = None

    for (action ← inputs) {
      cumulativeProbability += action._1
      if (weight <= cumulativeProbability && selectedAction.isEmpty)
        selectedAction = Some(action._2)
    }

    selectedAction.getOrElse {
      rd.shuffle(inputs).head._2
    }
  }

}

case class PostConditionBroken(actionDescription: String, errors: NonEmptyList[CornichonError]) extends CornichonError {
  def baseErrorMessage: String = s"A post-condition was broken for `$actionDescription`"
  override val causedBy: List[CornichonError] = errors.toList
}

case class NoValidTransitionAvailableForState(actionDescription: String) extends CornichonError {
  def baseErrorMessage: String = s"No outgoing transition found from `$actionDescription` to another action with valid pre-conditions"
}

sealed trait SuccessEndOfRun

case class EndActionReached(actionDescription: String, numberOfTransitions: Int) extends SuccessEndOfRun

case class MaxTransitionReached(numberOfTransitions: Int) extends SuccessEndOfRun