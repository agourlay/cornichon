package com.github.agourlay.cornichon.steps.regular.assertStep

import cats.Show
import cats.data.Validated._
import cats.data._
import cats.syntax.show._
import com.github.agourlay.cornichon.core.{ CornichonError, Done }
import com.github.agourlay.cornichon.util.Instances._

abstract class CollectionAssertion[A: Show] extends Assertion

case class CollectionNotEmptyAssertion[A: Show](collection: Traversable[A]) extends CollectionAssertion[A] {
  val validated: ValidatedNel[CornichonError, Done] =
    if (collection.nonEmpty) valid(Done) else invalidNel(CollectionNotEmptyAssertionError(collection))
}

case class CollectionNotEmptyAssertionError[A: Show](collection: Traversable[A]) extends CornichonError {
  val msg = s"collection expected to be non empty"
}

case class CollectionEmptyAssertion[A: Show](collection: Traversable[A]) extends CollectionAssertion[A] {
  val validated: ValidatedNel[CornichonError, Done] =
    if (collection.isEmpty) valid(Done) else invalidNel(CollectionEmptyAssertionError(collection))
}

case class CollectionEmptyAssertionError[A: Show](collection: Traversable[A]) extends CornichonError {
  val msg = s"expected collection to be empty but it contains:\n${collection.show}"
}

case class CollectionSizeAssertion[A: Show](collection: Traversable[A], size: Int) extends CollectionAssertion[A] {
  val validated: ValidatedNel[CornichonError, Done] =
    if (collection.size == size) valid(Done) else invalidNel(CollectionSizeAssertionError(collection, size))
}

case class CollectionSizeAssertionError[A: Show](collection: Traversable[A], size: Int) extends CornichonError {
  val msg = s"expected collection to have size '$size' but it contains '${collection.size} elements':\n${collection.show}"
}
