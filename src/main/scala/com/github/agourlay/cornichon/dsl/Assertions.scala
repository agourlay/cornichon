package com.github.agourlay.cornichon.dsl

import com.github.agourlay.cornichon.core.{ AssertStep, SimpleStepAssertion }

trait AssertionStep[A, B] {
  def is(expected: A): AssertStep[B]
}

trait CollectionAssertionStep[A, B] {
  def is(expected: A): AssertStep[Iterable[B]]
  def sizeIs(expected: Int): AssertStep[Int]
  def inOrder: CollectionAssertionStep[A, B]
  def contains(elements: A*): AssertStep[Boolean]
}

object CoreAssertion {
  case class SessionAssertion(key: String) extends AssertionStep[String, String] {
    def is(expected: String) = AssertStep(
      title = s"session key '$key' is '$expected'",
      action = s ⇒ SimpleStepAssertion(expected, s.get(key))
    )
  }

  case class SessionValuesAssertion(k1: String, k2: String) {
    def areEquals = AssertStep(
      title = s"content of key '$k1' is equal to content of key '$k2'",
      action = s ⇒ SimpleStepAssertion(s.get(k1), s.get(k2))
    )
  }
}
