package com.github.agourlay.cornichon.dsl

import com.github.agourlay.cornichon.steps.regular.AssertStep

trait CollectionAssertionSyntax[A, B] {
  def is(expected: A*): AssertStep[Iterable[B]]
  def hasSize(expected: Int): AssertStep[Int]
  def inOrder: CollectionAssertionSyntax[A, B]
  def contain(elements: A*): AssertStep[Boolean]
}

