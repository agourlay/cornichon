package com.github.agourlay.cornichon.dsl

import com.github.agourlay.cornichon.steps.regular.{ AssertStep, GenericAssertion }

trait AssertionSyntax[A, B] {
  def is(expected: A): AssertStep[B]
}

trait CollectionAssertionSyntax[A, B] {
  def is(expected: A*): AssertStep[Iterable[B]]
  def hasSize(expected: Int): AssertStep[Int]
  def inOrder: CollectionAssertionSyntax[A, B]
  def contain(elements: A*): AssertStep[Boolean]
}

object CoreAssertion {
  case class SessionAssertion(private val key: String) extends AssertionSyntax[String, String] {
    def is(expected: String) = AssertStep(
      title = s"session key '$key' is '$expected'",
      action = s ⇒ GenericAssertion(expected, s.get(key))
    )
  }

  case class SessionValuesAssertion(private val k1: String, private val k2: String) {
    def areEquals = AssertStep(
      title = s"content of key '$k1' is equal to content of key '$k2'",
      action = s ⇒ GenericAssertion(s.get(k1), s.get(k2))
    )
  }
}
