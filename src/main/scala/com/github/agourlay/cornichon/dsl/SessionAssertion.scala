package com.github.agourlay.cornichon.dsl

import com.github.agourlay.cornichon.core.SessionKey
import com.github.agourlay.cornichon.dsl.SessionAssertionErrors._
import com.github.agourlay.cornichon.json.JsonAssertions.JsonAssertion
import com.github.agourlay.cornichon.resolver.Resolver
import com.github.agourlay.cornichon.steps.regular.assertStep.{ AssertStep, CustomMessageEqualityAssertion, GenericEqualityAssertion }
import com.github.agourlay.cornichon.util.Instances._

case class SessionAssertion(
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

  def asJson = JsonAssertion(resolver, SessionKey(key))

}
