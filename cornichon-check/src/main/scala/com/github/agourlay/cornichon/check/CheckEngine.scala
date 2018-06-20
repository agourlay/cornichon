package com.github.agourlay.cornichon.check

import cats.data.NonEmptyList
import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.steps.regular.assertStep.AssertStep
import monix.eval.Task
import cats.syntax.either._

import scala.util.Random

class CheckEngine[A, B, C, D, E, F](engine: Engine, cs: CheckStep[A, B, C, D, E, F], modelRunner: ModelRunner[A, B, C, D, E, F], maxNumberOfTransitions: Int) {

  private val model = modelRunner.model
  private val genA = modelRunner.generatorA
  private val genB = modelRunner.generatorB
  private val genC = modelRunner.generatorC
  private val genD = modelRunner.generatorD
  private val genE = modelRunner.generatorE
  private val genF = modelRunner.generatorF

  private val rd = Random

  private def checkAssertions(initialRunState: RunState, assertions: List[AssertStep]): Task[List[Either[NonEmptyList[CornichonError], Done]]] =
    Task.gather(assertions.map(_.run(initialRunState)))

  private def returnValidTransitions(initialRunState: RunState)(transitions: List[(Double, State[A, B, C, D, E, F])]): Task[List[(Double, State[A, B, C, D, E, F], Boolean)]] =
    Task.gather {
      transitions.map { t ⇒
        checkAssertions(initialRunState, t._2.preConditions).map { preConditionsRes ⇒
          val failedConditions = preConditionsRes.collect { case Left(e) ⇒ e.toList }.flatten
          (t._1, t._2, failedConditions.isEmpty)
        }
      }
    }

  def run(initialRunState: RunState): Task[(RunState, FailedStep Either SuccessEndOfRun)] =
    //check precondition for starting state
    checkAssertions(initialRunState, model.startingState.preConditions).flatMap { startingIsValid ⇒
      startingIsValid.collect { case Left(e) ⇒ e }.flatMap(_.toList) match {
        case firstFailure :: others ⇒
          val errors = NonEmptyList.of(firstFailure, others: _*)
          Task.now((initialRunState, FailedStep(cs, errors).asLeft))
        case Nil ⇒
          // run first state
          loopRun(initialRunState, model.startingState, 0)
      }
    }

  private def loopRun(initialRunState: RunState, state: State[A, B, C, D, E, F], currentNumberOfTransitions: Int): Task[(RunState, FailedStep Either SuccessEndOfRun)] =
    if (currentNumberOfTransitions > maxNumberOfTransitions)
      Task.delay((initialRunState, MaxTransitionReached(maxNumberOfTransitions).asRight))
    else {
      runStateActionAndValidatePostConditions(initialRunState, state).flatMap {
        case (newState, res) ⇒
          res.fold(
            fs ⇒ Task.delay((newState, fs.asLeft)),
            _ ⇒ {
              // check available outgoing transitions
              model.transitions.get(state) match {
                case None ⇒
                  // No transitions defined -> end of run
                  Task.delay((newState, EndStateReached(state.description, currentNumberOfTransitions).asRight))
                case Some(transitions) ⇒
                  returnValidTransitions(newState)(transitions).flatMap { possibleNextStates ⇒
                    val (validNext, _) = possibleNextStates.partition(_._3)
                    if (validNext.isEmpty)
                      Task.delay((newState, FailedStep(cs, NonEmptyList.one(NoValidTransitionAvailableForState(state.description))).asLeft))
                    else {
                      // pick one transition according to the weight
                      val nextState = pickTransitionAccordingToProbability(rd, validNext)
                      loopRun(newState, nextState, currentNumberOfTransitions + 1)
                    }
                  }
              }
            }
          )
      }
    }

  // Assumes valid pre-conditions
  private def runStateActionAndValidatePostConditions(initialRunState: RunState, state: State[A, B, C, D, E, F]): Task[(RunState, FailedStep Either Done)] = {
    val s = initialRunState.session
    // run action
    state.action(genA.value(s), genB.value(s), genC.value(s), genD.value(s), genE.value(s), genF.value(s))
      .run(engine)(initialRunState)
      .flatMap {
        case (newState, res) ⇒
          res.fold(
            fs ⇒ Task.delay((newState, res)),
            _ ⇒ {
              // check post-conditions
              checkAssertions(newState, state.postConditions)
                .flatMap { afterConditionIsValid ⇒
                  val failures = afterConditionIsValid.collect { case Left(e) ⇒ e }.flatMap(_.toList)
                  failures match {
                    case firstFailure :: others ⇒
                      val errors = NonEmptyList.of(firstFailure, others: _*)
                      Task.now((initialRunState, FailedStep(cs, errors).asLeft))
                    case Nil ⇒
                      // run first state
                      Task.delay((newState, res))
                  }
                }
            }
          )
      }
  }

  //https://stackoverflow.com/questions/9330394/how-to-pick-an-item-by-its-probability
  private def pickTransitionAccordingToProbability(rd: Random, inputs: List[(Double, State[A, B, C, D, E, F], Boolean)]): State[A, B, C, D, E, F] = {
    val weight = rd.nextDouble()
    var cumulativeProbability = 0.0

    var selectedState: Option[State[A, B, C, D, E, F]] = None

    for (state ← inputs) {
      cumulativeProbability += state._1
      if (weight <= cumulativeProbability && selectedState.isEmpty)
        selectedState = Some(state._2)
    }

    selectedState.getOrElse {
      rd.shuffle(inputs).head._2
    }
  }

}

case class NoValidTransitionAvailableForState(state: String) extends CornichonError {
  def baseErrorMessage: String = s"No outgoing transition found to a state with valid pre-conditions from $state"
}

sealed trait SuccessEndOfRun

case class EndStateReached(state: String, numberOfTransitions: Int) extends SuccessEndOfRun

case class MaxTransitionReached(numberOfTransitions: Int) extends SuccessEndOfRun