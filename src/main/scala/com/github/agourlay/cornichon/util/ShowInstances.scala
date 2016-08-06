package com.github.agourlay.cornichon.util

import cats.Show

import cats.syntax.show._

// Most of those are not yet used - this is a preparation for the GenericAssertion[A : Show]
trait ShowInstances {

  implicit val showString = new Show[String] {
    def show(s: String) = s
  }

  implicit val showBoolean = new Show[Boolean] {
    def show(b: Boolean) = b.toString
  }

  implicit val showInt = new Show[Int] {
    def show(i: Int) = i.toString
  }

  implicit def showIterable[A: Show]: Show[Iterable[A]] = new Show[Iterable[A]] {
    def show(fa: Iterable[A]): String =
      fa.toIterator.map(_.show).mkString("Iterable(", ", ", ")")
  }

}

object ShowInstances extends ShowInstances
