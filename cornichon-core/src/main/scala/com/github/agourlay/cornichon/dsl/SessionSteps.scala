package com.github.agourlay.cornichon.dsl

import cats.instances.string._
import cats.instances.boolean._
import cats.syntax.either._
import cats.syntax.show._
import com.github.agourlay.cornichon.core.SessionKey
import com.github.agourlay.cornichon.json.JsonSteps.JsonStepBuilder
import com.github.agourlay.cornichon.matchers.MatcherResolver
import com.github.agourlay.cornichon.resolver.PlaceholderResolver
import com.github.agourlay.cornichon.steps.regular.assertStep.{ AssertStep, Assertion, CustomMessageEqualityAssertion, GenericEqualityAssertion }

object SessionSteps {

  case class SessionStepBuilder(
      private val placeholderResolver: PlaceholderResolver,
      private val matcherResolver: MatcherResolver,
      private val key: String,
      private val indice: Option[Int] = None
  ) {

    def atIndex(indice: Int) = copy(indice = Some(indice))

    def is(expected: String) = AssertStep(
      title = s"session key '$key' is '$expected'",
      action = s ⇒ Assertion.either {
        for {
          filledPlaceholders ← placeholderResolver.fillPlaceholders(expected)(s)
          keyValue ← s.get(key, indice)
        } yield GenericEqualityAssertion(filledPlaceholders, keyValue)
      }
    )

    def isPresent = AssertStep(
      title = s"session contains key '$key'",
      action = s ⇒ {
        val predicate = s.getOpt(key, indice).isDefined
        CustomMessageEqualityAssertion(true, predicate, () ⇒ keyIsAbsentError(key, s.show))
      }
    )

    def isAbsent = AssertStep(
      title = s"session does not contain key '$key'",
      action = s ⇒
        s.getOpt(key, indice) match {
          case None        ⇒ Assertion.alwaysValid
          case Some(value) ⇒ CustomMessageEqualityAssertion(false, true, () ⇒ keyIsPresentError(key, value))
        }
    )

    def asJson = JsonStepBuilder(placeholderResolver, matcherResolver, SessionKey(key))

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
