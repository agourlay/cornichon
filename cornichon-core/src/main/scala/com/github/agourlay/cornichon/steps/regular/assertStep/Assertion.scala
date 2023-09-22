package com.github.agourlay.cornichon.steps.regular.assertStep

import cats.data._
import cats.syntax.validated._
import cats.syntax.apply._
import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.core.Done._

trait Assertion { self =>
  def validated: ValidatedNel[CornichonError, Done]

  def and(other: Assertion): Assertion = new Assertion {
    val validated = self.validated *> other.validated
  }

  def andAll(others: Seq[Assertion]): Assertion =
    if (others.isEmpty)
      self
    else
      new Assertion {
        val validated = others.fold(self)(_ and _).validated
      }

  def or(other: Assertion): Assertion = new Assertion {
    val validated =
      if (self.validated.isValid || other.validated.isValid)
        validDone
      else
        self.validated *> other.validated
  }
}

object Assertion {

  val alwaysValid: Assertion = new Assertion { val validated = validDone }
  def failWith(error: String): Assertion = new Assertion { val validated = BasicError(error).invalidNel }
  def failWith(error: CornichonError): Assertion = new Assertion { val validated = error.invalidNel }

  def either(v: Either[CornichonError, Assertion]): Assertion = v.fold(e => failWith(e), identity)

  def all(assertions: Seq[Assertion]): Assertion = assertions.reduce((acc, assertion) => acc.and(assertion))
  def any(assertions: Seq[Assertion]): Assertion = assertions.reduce((acc, assertion) => acc.or(assertion))
}