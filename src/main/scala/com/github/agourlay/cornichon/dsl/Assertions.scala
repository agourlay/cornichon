package com.github.agourlay.cornichon.dsl

import com.github.agourlay.cornichon.core.{ SimpleStepAssertion, RunnableStep }

trait AssertionStep[A, B] {
  def is(expected: A): RunnableStep[B]
}

trait CollectionAssertionStep[A, B] {
  def is(expected: A): RunnableStep[Iterable[B]]
  def sizeIs(expected: Int): RunnableStep[Int]
  def inOrder: CollectionAssertionStep[A, B]
  def contains(elements: A*): RunnableStep[Boolean]
}

object CoreAssertion {
  case class SessionAssertion(key: String) extends AssertionStep[String, String] {
    def is(expected: String) = RunnableStep(
      title = s"session key '$key' is '$expected'",
      action = s ⇒ (s, SimpleStepAssertion(expected, s.get(key)))
    )
  }

  case class SessionValuesAssertion(k1: String, k2: String) {
    def areEquals = RunnableStep(
      title = s"content of key '$k1' is equal to content of key '$k2'",
      action = s ⇒ (s, SimpleStepAssertion(s.get(k1), s.get(k2)))
    )
  }
}
