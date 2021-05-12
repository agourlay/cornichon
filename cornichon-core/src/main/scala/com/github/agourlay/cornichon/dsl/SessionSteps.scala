package com.github.agourlay.cornichon.dsl

import cats.instances.either._
import cats.instances.set._
import cats.instances.vector._
import cats.syntax.show._
import cats.syntax.traverse._
import com.github.agourlay.cornichon.core.SessionKey
import com.github.agourlay.cornichon.json.JsonSteps._
import com.github.agourlay.cornichon.resolver.Resolvable
import com.github.agourlay.cornichon.steps.regular.assertStep._

import scala.util.matching.Regex

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

    def asJson: JsonValuesStepBuilder = JsonValuesStepBuilder(k1, k2)
  }

  case class SessionStepBuilder(
      private val sessionKey: SessionKey,
      private val prettySessionKeyTitle: Option[String] = None) {
    private val key = sessionKey.name
    private val index = sessionKey.index
    private val target = prettySessionKeyTitle.getOrElse(s"session key '$key'")

    def atIndex(index: Int): SessionStepBuilder = copy(sessionKey = sessionKey.copy(index = Some(index)))

    def is(expected: String): AssertStep = isImpl(expected, negate = false)

    def isNot(expected: String): AssertStep = isImpl(expected, negate = true)

    private def isImpl(expected: String, negate: Boolean) = AssertStep(
      title = s"$target ${if (negate) "is not" else "is"} '$expected'",
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

    def containsString(expectedPart: String): AssertStep = AssertStep(
      title = s"$target contains string '$expectedPart'",
      action = sc => Assertion.either {
        for {
          sessionValue <- sc.session.get(key)
          resolvedExpected <- sc.fillPlaceholders(expectedPart)
        } yield StringContainsAssertion(sessionValue, resolvedExpected)
      }
    )

    def matchesRegex(expectedRegex: Regex): AssertStep = AssertStep(
      title = s"$target matches '$expectedRegex'",
      action = sc => Assertion.either {
        for {
          sessionValue <- sc.session.get(key)
        } yield RegexAssertion(sessionValue, expectedRegex)
      }
    )

    def hasEqualCurrentAndPreviousValues: AssertStep = AssertStep(
      title = s"$target has equal current and previous values",
      action = sc => Assertion.either {
        for {
          current <- sc.session.get(key)
          previous <- sc.session.getMandatoryPrevious(key)
        } yield GenericEqualityAssertion(current, previous)
      }
    )

    def hasDifferentCurrentAndPreviousValues: AssertStep = AssertStep(
      title = s"$target has different current and previous values",
      action = sc => Assertion.either {
        for {
          current <- sc.session.get(key)
          previous <- sc.session.getMandatoryPrevious(key)
        } yield GenericEqualityAssertion(current, previous, negate = true)
      }
    )

    // (previousValue, currentValue) => Assertion
    def compareWithPreviousValue(comp: (String, String) => Assertion): AssertStep = AssertStep(
      title = s"compare previous & current value of $target",
      action = sc => Assertion.either {
        for {
          current <- sc.session.get(key)
          previous <- sc.session.getMandatoryPrevious(key)
        } yield comp(previous, current)
      }
    )

    def asJson: JsonStepBuilder = JsonStepBuilder(sessionKey, prettySessionKeyTitle)
  }

  case class SessionHistoryStepBuilder(private val sessionKey: String) {
    def containsExactly[A: Resolvable](elements: A*): AssertStep = {
      val prettyElements = elements.mkString(" and ")
      val title = s"$sessionKey history contains exactly\n$prettyElements"
      historyContainsExactlyElmt(title, elements)
    }

    private def historyContainsExactlyElmt[A: Resolvable](title: String, expectedElements: Seq[A]) =
      AssertStep(
        title = title,
        action = sc => Assertion.either {
          for {
            actualValues <- sc.session.getHistory(sessionKey)
            expectedResolvedElements <- expectedElements.toVector
              .traverse(e => sc.fillPlaceholders(e))
              .map(_.map(Resolvable[A].toResolvableForm))
          } yield CollectionsContainSameElements(expectedResolvedElements, actualValues)
        }
      )
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
