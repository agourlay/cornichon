package com.github.agourlay.cornichon.steps.regular.assertStep

import cats.Show
import cats.data.Validated._
import cats.data._
import cats.syntax.show._
import com.github.agourlay.cornichon.core.{ CornichonError, Done }
import com.github.agourlay.cornichon.util.Instances._

abstract class CollectionAssertion[A: Show] extends Assertion {
  def withName(collectionName: String): CollectionAssertion[A]
}

case class CollectionNotEmptyAssertion[A: Show](collection: Iterable[A], name: String = "collection") extends CollectionAssertion[A] {
  def withName(collectionName: String) = copy(name = collectionName)

  val validated: ValidatedNel[CornichonError, Done] =
    if (collection.nonEmpty) valid(Done) else invalidNel(CollectionNotEmptyAssertionError(collection, name))
}

case class CollectionNotEmptyAssertionError[A: Show](collection: Iterable[A], name: String) extends CornichonError {
  val msg = s"'$name' expected to be non empty"
}

case class CollectionEmptyAssertion[A: Show](collection: Iterable[A], name: String = "collection") extends CollectionAssertion[A] {
  def withName(collectionName: String) = copy(name = collectionName)

  val validated: ValidatedNel[CornichonError, Done] =
    if (collection.isEmpty) valid(Done) else invalidNel(CollectionEmptyAssertionError(collection, name))
}

case class CollectionEmptyAssertionError[A: Show](collection: Iterable[A], name: String) extends CornichonError {
  val msg = s"expected '$name' to be empty but it contains:\n${collection.show}"
}

case class CollectionSizeAssertion[A: Show](collection: Iterable[A], size: Int, name: String = "collection") extends CollectionAssertion[A] {
  def withName(collectionName: String) = copy(name = collectionName)

  val validated: ValidatedNel[CornichonError, Done] =
    if (collection.size == size) valid(Done) else invalidNel(CollectionSizeAssertionError(collection, size, name))
}

case class CollectionSizeAssertionError[A: Show](collection: Iterable[A], size: Int, name: String) extends CornichonError {
  val msg = s"expected '$name' to have size '$size' but it actually contains '${collection.size} elements':\n${collection.show}"
}

