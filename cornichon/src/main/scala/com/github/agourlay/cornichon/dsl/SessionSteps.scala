package com.github.agourlay.cornichon.dsl

import com.github.agourlay.cornichon.core.SessionKey
import com.github.agourlay.cornichon.json.JsonSteps.JsonStepBuilder
import com.github.agourlay.cornichon.resolver.Resolver
import com.github.agourlay.cornichon.steps.regular.assertStep.{ AssertStep, CustomMessageEqualityAssertion, GenericEqualityAssertion }
import com.github.agourlay.cornichon.util.Instances._

object SessionSteps {

  case class SessionStepBuilder(
      private val resolver: Resolver,
      private val key: String,
      private val indice: Option[Int] = None
  ) {

    def atIndex(indice: Int) = copy(indice = Some(indice))

    def is(expected: String) = AssertStep(
      title = s"session key '$key' is '$expected'",
      action = s ⇒ GenericEqualityAssertion(resolver.fillPlaceholdersUnsafe(expected)(s), s.get(key, indice))
    )

    def isPresent = AssertStep(
      title = s"session contains key '$key'",
      action = s ⇒ {
      val predicate = s.getOpt(key, indice).isDefined
      CustomMessageEqualityAssertion(true, predicate, keyIsAbsentError(key, s.prettyPrint))
    }
    )

    def isAbsent = AssertStep(
      title = s"session does not contain key '$key'",
      action = s ⇒ CustomMessageEqualityAssertion(None, s.getOpt(key, indice), keyIsPresentError(key))
    )

    def asJson = JsonStepBuilder(resolver, SessionKey(key))

  }

  def keyIsPresentError(keyName: String): Option[String] ⇒ String = resOpt ⇒ {
    s"""expected key '$keyName' to be absent from session but it was found with value :
       |${resOpt.get}""".stripMargin
  }

  def keyIsAbsentError(keyName: String, session: String): Boolean ⇒ String = resFalse ⇒ {
    s"""expected key '$keyName' to be present but it was not found in the session :
       |$session""".stripMargin
  }

}
