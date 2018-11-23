package com.github.agourlay.cornichon.check.checkModel

import cats.data.NonEmptyList
import com.github.agourlay.cornichon.core._
import monix.eval.Task
import cats.syntax.either._
import com.github.agourlay.cornichon.check.Generator

import scala.util.Random

class CheckModelEngine[A, B, C, D, E, F](
    cs: CheckModelStep[A, B, C, D, E, F],
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
  private def checkStepConditions(engine: Engine, initialRunState: RunState)(assertion: Step): Task[Either[FailedStep, Done]] =
    assertion.run(engine)(initialRunState).map(_._2)

  private def validTransitions(engine: Engine, initialRunState: RunState)(transitions: List[(Double, PropertyN[A, B, C, D, E, F])]): Task[List[(Double, PropertyN[A, B, C, D, E, F], Boolean)]] =
    Task.gather {
      transitions.map {
        case (weight, actions) ⇒
          checkStepConditions(engine, initialRunState)(actions.preCondition)
            .map(preConditionsRes ⇒ (weight, actions, preConditionsRes.isRight))
      }
    }

  def run(engine: Engine, initialRunState: RunState): Task[(RunState, FailedStep Either SuccessEndOfRun)] =
    //check precondition for starting action
    checkStepConditions(engine, initialRunState)(model.entryPoint.preCondition).flatMap {
      case Right(_) ⇒
        // run first state
        loopRun(engine, initialRunState, model.entryPoint, 0)
      case Left(failedStep) ⇒
        Task.now((initialRunState, FailedStep(cs, failedStep.errors).asLeft))
    }

  private def loopRun(engine: Engine, initialRunState: RunState, action: PropertyN[A, B, C, D, E, F], currentNumberOfTransitions: Int): Task[(RunState, FailedStep Either SuccessEndOfRun)] =
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
  private def runActionAndValidatePostConditions(engine: Engine, initialRunState: RunState, action: PropertyN[A, B, C, D, E, F]): Task[(RunState, FailedStep Either Done)] = {
    val s = initialRunState.session
    // Init Gens
    val ga = genA.value(s)
    val gb = genB.value(s)
    val gc = genC.value(s)
    val gd = genD.value(s)
    val ge = genE.value(s)
    val gf = genF.value(s)
    // Generate effect
    val invariantStep = action.invariantN(ga, gb, gc, gd, ge, gf)
    val actionNameLog = InfoLogInstruction(s"${action.description}", initialRunState.depth)
    invariantStep.run(engine)(initialRunState.appendLog(actionNameLog))
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

case class NoValidTransitionAvailableForState(actionDescription: String) extends CornichonError {
  def baseErrorMessage: String = s"No outgoing transition found from `$actionDescription` to another action with valid pre-conditions"
}

sealed trait SuccessEndOfRun

case class EndActionReached(actionDescription: String, numberOfTransitions: Int) extends SuccessEndOfRun

case class MaxTransitionReached(numberOfTransitions: Int) extends SuccessEndOfRun