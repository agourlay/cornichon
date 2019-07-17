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
  private def checkStepConditions(runState: RunState)(assertion: Step): Task[Either[FailedStep, Done]] =
    assertion.stateUpdate.runA(runState)

  private def validTransitions(runState: RunState)(transitions: List[(Int, PropertyN[A, B, C, D, E, F])]): Task[List[(Int, PropertyN[A, B, C, D, E, F], Boolean)]] = {
    val stepsConditionValidations = transitions.map {
      case (weight, properties) ⇒
        checkStepConditions(runState)(properties.preCondition)
          .map(preConditionsRes ⇒ (weight, properties, preConditionsRes.isRight))
    }
    Task.gather(stepsConditionValidations)
      .map(_.sortBy(_._1)) // We sort the result because `Task.gather` is non-deterministic to not break the seed
  }

  def run(runState: RunState): Task[(RunState, FailedStep Either SuccessEndOfRun)] =
    //check precondition for starting property
    checkStepConditions(runState)(model.entryPoint.preCondition).flatMap {
      case Right(_) ⇒
        // run first state
        loopRun(runState, model.entryPoint, 0)
      case Left(failedStep) ⇒
        Task.now((runState, FailedStep(cs, failedStep.errors).asLeft))
    }

  private def loopRun(runState: RunState, property: PropertyN[A, B, C, D, E, F], currentNumberOfTransitions: Int): Task[(RunState, FailedStep Either SuccessEndOfRun)] =
    if (currentNumberOfTransitions > maxNumberOfTransitions)
      Task.now((runState, MaxTransitionReached(maxNumberOfTransitions).asRight))
    else
      runPropertyAndValidatePostConditions(runState, property).flatMap {
        case (newState, Left(fs)) ⇒
          Task.now((newState, fs.asLeft))
        case (newState, Right(_)) ⇒
          // check available outgoing transitions
          model.transitions.get(property) match {
            case None ⇒
              // no transitions defined -> end of run
              Task.now((newState, EndPropertyReached(property.description, currentNumberOfTransitions).asRight))
            case Some(transitions) ⇒
              validTransitions(newState)(transitions).flatMap { possibleNextStates ⇒
                val validNext = possibleNextStates.filter(_._3)
                if (validNext.isEmpty) {
                  val error = NoValidTransitionAvailableForState(property.description)
                  val noTransitionLog = FailureLogInstruction(error.baseErrorMessage, runState.depth)
                  Task.now((newState.recordLog(noTransitionLog), FailedStep(cs, NonEmptyList.one(error)).asLeft))
                } else {
                  // pick one transition according to the weight
                  val nextProperty = pickTransitionAccordingToProbability(rd, validNext)
                  loopRun(newState, nextProperty, currentNumberOfTransitions + 1)
                }
              }
          }
      }

  // Assumes valid pre-conditions
  private def runPropertyAndValidatePostConditions(runState: RunState, property: PropertyN[A, B, C, D, E, F]): Task[(RunState, FailedStep Either Done)] = {
    val s = runState.session
    // Init Gens
    val ga = genA.value(s)
    val gb = genB.value(s)
    val gc = genC.value(s)
    val gd = genD.value(s)
    val ge = genE.value(s)
    val gf = genF.value(s)
    // Generate effect
    val invariantStep = property.invariantN(ga, gb, gc, gd, ge, gf)
    val propertyNameLog = InfoLogInstruction(s"${property.description}", runState.depth)
    invariantStep.runStep(runState.recordLog(propertyNameLog))
  }

  //https://stackoverflow.com/questions/9330394/how-to-pick-an-item-by-its-probability
  private def pickTransitionAccordingToProbability[Z](rd: Random, inputs: List[(Int, Z, Boolean)]): Z = {
    val weight = rd.nextInt(100)
    var cumulativeProbability: Int = 0
    var selected: Option[Z] = None

    for (item ← inputs) {
      cumulativeProbability += item._1
      if (weight <= cumulativeProbability && selected.isEmpty) selected = Some(item._2)
    }

    selected.getOrElse(rd.shuffle(inputs).head._2)
  }

}

case class NoValidTransitionAvailableForState(description: String) extends CornichonError {
  def baseErrorMessage: String = s"No outgoing transition found from `$description` to another property with valid pre-conditions"
}

sealed trait SuccessEndOfRun

case class EndPropertyReached(description: String, numberOfTransitions: Int) extends SuccessEndOfRun

case class MaxTransitionReached(numberOfTransitions: Int) extends SuccessEndOfRun