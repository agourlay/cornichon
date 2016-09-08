package com.github.agourlay.cornichon.dsl

import com.github.agourlay.cornichon.core.SessionKey
import com.github.agourlay.cornichon.dsl.SessionAssertionErrors._
import com.github.agourlay.cornichon.json.JsonAssertions.JsonAssertion
import com.github.agourlay.cornichon.resolver.Resolver
import com.github.agourlay.cornichon.steps.regular.assertStep.{ AssertStep, CustomMessageAssertion, GenericAssertion }
import com.github.agourlay.cornichon.util.ShowInstances._

case class SessionAssertion(
    private val resolver: Resolver,
    private val key: String,
    private val indice: Option[Int] = None
) {

  def atIndex(indice: Int) = copy(indice = Some(indice))

  def is(expected: String) = AssertStep(
    title = s"session key '$key' is '$expected'",
    action = s ⇒ GenericAssertion(expected, s.get(key, indice))
  )

  def isEqualToSessionValue(other: String, indice: Option[Int] = None) = AssertStep(
    title = s"content of session key '$key' is equal to the content of key '$other'",
    action = s ⇒ GenericAssertion(s.get(key), s.get(other))
  )

  def isPresent = AssertStep[Boolean](
    title = s"session contains key '$key'",
    action = s ⇒ {
    val predicate = s.getOpt(key, indice).isDefined
    CustomMessageAssertion(true, predicate, keyIsAbsentError(key, s.prettyPrint))
  }
  )

  def isAbsent = AssertStep(
    title = s"session does not contain key '$key'",
    action = s ⇒ CustomMessageAssertion(None, s.getOpt(key, indice), keyIsPresentError(key))
  )

  def asJson = JsonAssertion(resolver, SessionKey(key))

}
