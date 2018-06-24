package com.github.agourlay.cornichon.check

import java.util.concurrent.ConcurrentLinkedQueue

import cats.data.NonEmptyList
import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.steps.regular.assertStep.AssertStep
import monix.eval.Task
import cats.syntax.either._
import com.github.agourlay.cornichon.util.Printing

import collection.JavaConverters._
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

  private def returnValidTransitions(initialRunState: RunState)(transitions: List[(Double, ActionN[A, B, C, D, E, F])]): Task[List[(Double, ActionN[A, B, C, D, E, F], Boolean)]] =
    Task.gather {
      transitions.map { t ⇒
        checkAssertions(initialRunState, t._2.preConditions).map { preConditionsRes ⇒
          val failedConditions = preConditionsRes.collect { case Left(e) ⇒ e.toList }.flatten
          (t._1, t._2, failedConditions.isEmpty)
        }
      }
    }

  def run(initialRunState: RunState): Task[(RunState, FailedStep Either SuccessEndOfRun)] =
    //check precondition for starting action
    checkAssertions(initialRunState, model.startingAction.preConditions).flatMap { startingIsValid ⇒
      startingIsValid.collect { case Left(e) ⇒ e }.flatMap(_.toList) match {
        case firstFailure :: others ⇒
          val errors = NonEmptyList.of(firstFailure, others: _*)
          Task.now((initialRunState, FailedStep(cs, errors).asLeft))
        case Nil ⇒
          // run first state
          loopRun(initialRunState, model.startingAction, 0)
      }
    }

  private def loopRun(initialRunState: RunState, action: ActionN[A, B, C, D, E, F], currentNumberOfTransitions: Int): Task[(RunState, FailedStep Either SuccessEndOfRun)] =
    if (currentNumberOfTransitions > maxNumberOfTransitions)
      Task.delay((initialRunState, MaxTransitionReached(maxNumberOfTransitions).asRight))
    else {
      runActionAndValidatePostConditions(initialRunState, action).flatMap {
        case (newState, res) ⇒
          res.fold(
            fs ⇒ Task.delay((newState, fs.asLeft)),
            _ ⇒ {
              // check available outgoing transitions
              model.transitions.get(action) match {
                case None ⇒
                  // No transitions defined -> end of run
                  Task.delay((newState, EndActionReached(action.description, currentNumberOfTransitions).asRight))
                case Some(transitions) ⇒
                  returnValidTransitions(newState)(transitions).flatMap { possibleNextStates ⇒
                    val (validNext, _) = possibleNextStates.partition(_._3)
                    if (validNext.isEmpty) {
                      val error = NoValidTransitionAvailableForState(action.description)
                      Task.delay((newState, FailedStep(cs, NonEmptyList.one(error)).asLeft))
                    } else {
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
  private def runActionAndValidatePostConditions(initialRunState: RunState, action: ActionN[A, B, C, D, E, F]): Task[(RunState, FailedStep Either Done)] = {
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
    // Log queue now contains the generated values for the run
    val generatedValues = logQueue.asScala.toList.sortBy(_._1)
    val generatedValuesLog = Printing.printArrowPairs(generatedValues)
    val fullMessage = s"${action.description} ${if (generatedValuesLog.isEmpty) "" else s"with values [$generatedValuesLog]"}"
    val actionNameLog = InfoLogInstruction(fullMessage, initialRunState.depth)
    effect.run(engine)(initialRunState.appendLog(actionNameLog))
      .flatMap {
        case (newState, res) ⇒
          res.fold(
            fs ⇒ Task.delay((newState, res)),
            _ ⇒ {
              // check post-conditions
              checkAssertions(newState, action.postConditions)
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
  def baseErrorMessage: String = s"No outgoing transition found from $actionDescription to another action with valid pre-conditions"
}

sealed trait SuccessEndOfRun

case class EndActionReached(actionDescription: String, numberOfTransitions: Int) extends SuccessEndOfRun

case class MaxTransitionReached(numberOfTransitions: Int) extends SuccessEndOfRun