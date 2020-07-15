package com.github.agourlay.cornichon.dsl

import cats.instances.string._
import cats.instances.boolean._
import cats.syntax.show._
import com.github.agourlay.cornichon.core.SessionKey
import com.github.agourlay.cornichon.json.JsonSteps.JsonStepBuilder
import com.github.agourlay.cornichon.steps.regular.assertStep.{ AssertStep, Assertion, CustomMessageEqualityAssertion, GenericEqualityAssertion }

object SessionSteps {

  case class SessionValuesStepBuilder(
      private val k1: String,
      private val k2: String) {

    def areEquals: AssertStep = AssertStep(
      title = s"content of session key '$k1' is equal to content of key '$k2'",
      action = sc => Assertion.either {
        for {
          v1 <- sc.session.get(k1)
          v2 <- sc.session.get(k2)
        } yield GenericEqualityAssertion(v1, v2)
      }
    )

    def areNotEquals: AssertStep = AssertStep(
      title = s"content of session key '$k1' is not equal to content of key '$k2'",
      action = sc => Assertion.either {
        for {
          v1 <- sc.session.get(k1)
          v2 <- sc.session.get(k2)
        } yield GenericEqualityAssertion(v1, v2, negate = true)
      }
    )
  }

  case class SessionStepBuilder(
      private val key: String,
      private val index: Option[Int] = None
  ) {

    def atIndex(index: Int): SessionStepBuilder = copy(index = Some(index))

    def is(expected: String): AssertStep = isImpl(expected, negate = false)

    def isNot(expected: String): AssertStep = isImpl(expected, negate = true)

    private def isImpl(expected: String, negate: Boolean) = AssertStep(
      title = s"session key '$key' ${if (negate) "is not" else "is"} '$expected'",
      action = sc => Assertion.either {
        for {
          filledPlaceholders <- sc.fillPlaceholders(expected)
          keyValue <- sc.session.get(key, index)
        } yield GenericEqualityAssertion(filledPlaceholders, keyValue, negate = negate)
      }
    )

    def isPresent: AssertStep = AssertStep(
      title = s"session contains key '$key'",
      action = sc => {
        val predicate = sc.session.getOpt(key, index).isDefined
        CustomMessageEqualityAssertion(true, predicate, () => keyIsAbsentError(key, sc.session.show))
      }
    )

    def isAbsent: AssertStep = AssertStep(
      title = s"session does not contain key '$key'",
      action = sc =>
        sc.session.getOpt(key, index) match {
          case None        => Assertion.alwaysValid
          case Some(value) => CustomMessageEqualityAssertion(false, true, () => keyIsPresentError(key, value))
        }
    )

    def hasEqualCurrentAndPreviousValues: AssertStep = AssertStep(
      title = s"session key '$key' has equal current and previous values",
      action = sc => Assertion.either {
        for {
          current <- sc.session.get(key)
          previous <- sc.session.getPrevious(key)
        } yield previous match {
          case None                => Assertion.failWith(s"no previous value available to compare to current $current")
          case Some(previousValue) => GenericEqualityAssertion(current, previousValue)
        }
      }
    )

    def hasDifferentCurrentAndPreviousValues: AssertStep = AssertStep(
      title = s"session key '$key' has different current and previous values",
      action = sc => Assertion.either {
        for {
          current <- sc.session.get(key)
          previous <- sc.session.getPrevious(key)
        } yield previous match {
          case None                => Assertion.failWith(s"no previous value available to compare to current $current")
          case Some(previousValue) => GenericEqualityAssertion(current, previousValue, negate = true)
        }
      }
    )

    // (previousValue, currentValue) => Assertion
    def compareWithPreviousValue(comp: (String, String) => Assertion): AssertStep = AssertStep(
      title = s"compare previous & current value of session key '$key'",
      action = sc => Assertion.either {
        for {
          current <- sc.session.get(key)
          previous <- sc.session.getPrevious(key)
        } yield previous match {
          case None                => Assertion.failWith(s"no previous value available to compare to current $current")
          case Some(previousValue) => comp(previousValue, current)
        }
      }
    )

    def asJson: JsonStepBuilder = JsonStepBuilder(SessionKey(key))

  }

  def keyIsPresentError(keyName: String, keyValue: String): String = {
    s"""expected key '$keyName' to be absent from session but it was found with value :
       |$keyValue""".stripMargin
  }

  def keyIsAbsentError(keyName: String, session: String): String = {
    s"""expected key '$keyName' to be present but it was not found in the session :
       |$session""".stripMargin
  }

}
