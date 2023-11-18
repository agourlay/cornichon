package com.github.agourlay.cornichon.steps.regular.assertStep

import cats.Show
import cats.syntax.show._
import cats.syntax.validated._

import com.github.agourlay.cornichon.core.CornichonError
import com.github.agourlay.cornichon.core.Done._
import CollectionAssertionInstances._

abstract class CollectionAssertion[A] extends Assertion

case class CollectionNotEmptyAssertion[A](collection: Iterable[A], name: String) extends CollectionAssertion[A] {
  lazy val validated = if (collection.nonEmpty) validDone else CollectionNotEmptyAssertionError(collection, name).invalidNel
}

case class CollectionNotEmptyAssertionError[A](collection: Iterable[A], name: String) extends CornichonError {
  lazy val baseErrorMessage = s"'$name' expected to be non empty"
}

case class CollectionEmptyAssertion[A: Show](collection: Iterable[A], name: String) extends CollectionAssertion[A] {
  lazy val validated = if (collection.isEmpty) validDone else CollectionEmptyAssertionError(collection, name).invalidNel
}

case class CollectionEmptyAssertionError[A: Show](collection: Iterable[A], name: String) extends CornichonError {
  lazy val baseErrorMessage = s"expected '$name' to be empty but it contains:\n${collection.show}"
}

case class CollectionSizeAssertion[A: Show](collection: Iterable[A], size: Int, name: String) extends CollectionAssertion[A] {
  lazy val validated = if (collection.size == size) validDone else CollectionSizeAssertionError(collection, size, name).invalidNel
}

case class CollectionSizeAssertionError[A: Show](collection: Iterable[A], size: Int, name: String) extends CornichonError {
  lazy val baseErrorMessage = s"expected '$name' to have size '$size' but it actually contains '${collection.size} elements':\n${collection.show}"
}

case class CollectionsContainSameElements[A: Show](right: Seq[A], left: Seq[A]) extends CollectionAssertion[A] {
  lazy val validated = {
    val deleted = right.diff(left)
    val added = left.diff(right)
    if (added.isEmpty && deleted.isEmpty)
      validDone
    else
      CollectionsContainSameElementsAssertionError(added, deleted).invalidNel
  }
}

case class CollectionsContainSameElementsAssertionError[A: Show](added: Seq[A], deleted: Seq[A]) extends CornichonError {
  lazy val baseErrorMessage =
    s"""|Non ordered diff. between actual result and expected result is :
        |${if (added.isEmpty) "" else "added elements:\n" + added.iterator.map(_.show).mkString("\n")}
        |${if (deleted.isEmpty) "" else "deleted elements:\n" + deleted.iterator.map(_.show).mkString("\n")}
      """.stripMargin.trim
}

object CollectionAssertionInstances {
  // only used for error rendering
  implicit def showIterable[A: Show]: Show[Iterable[A]] = Show.show { fa =>
    fa.iterator.map(_.show).mkString("(", ", ", ")")
  }
}