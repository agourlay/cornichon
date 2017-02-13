package com.github.agourlay.cornichon.steps.regular.assertStep

import cats.Show
import cats.data.Validated._
import cats.data._
import cats.syntax.show._
import com.github.agourlay.cornichon.core.{ CornichonError, Done }
import com.github.agourlay.cornichon.dsl.Instances._

abstract class CollectionAssertion[A: Show] extends Assertion

case class CollectionNotEmptyAssertion[A: Show](collection: Iterable[A], name: String = "collection") extends CollectionAssertion[A] {
  def withName(collectionName: String) = copy(name = collectionName)

  val validated: ValidatedNel[CornichonError, Done] =
    if (collection.nonEmpty) valid(Done) else invalidNel(CollectionNotEmptyAssertionError(collection, name))
}

case class CollectionNotEmptyAssertionError[A: Show](collection: Iterable[A], name: String) extends CornichonError {
  val baseErrorMessage = s"'$name' expected to be non empty"
}

case class CollectionEmptyAssertion[A: Show](collection: Iterable[A], name: String = "collection") extends CollectionAssertion[A] {
  def withName(collectionName: String) = copy(name = collectionName)

  val validated: ValidatedNel[CornichonError, Done] =
    if (collection.isEmpty) valid(Done) else invalidNel(CollectionEmptyAssertionError(collection, name))
}

case class CollectionEmptyAssertionError[A: Show](collection: Iterable[A], name: String) extends CornichonError {
  val baseErrorMessage = s"expected '$name' to be empty but it contains:\n${collection.show}"
}

case class CollectionSizeAssertion[A: Show](collection: Iterable[A], size: Int, name: String = "collection") extends CollectionAssertion[A] {
  def withName(collectionName: String) = copy(name = collectionName)

  val validated: ValidatedNel[CornichonError, Done] =
    if (collection.size == size) valid(Done) else invalidNel(CollectionSizeAssertionError(collection, size, name))
}

case class CollectionSizeAssertionError[A: Show](collection: Iterable[A], size: Int, name: String) extends CornichonError {
  val baseErrorMessage = s"expected '$name' to have size '$size' but it actually contains '${collection.size} elements':\n${collection.show}"
}

case class CollectionsContainSameElements[A: Show](right: Seq[A], left: Seq[A]) extends CollectionAssertion[A] {
  val validated: ValidatedNel[CornichonError, Done] = {
    val deleted = right.diff(left)
    val added = left.diff(right)
    if (added.isEmpty && deleted.isEmpty)
      valid(Done)
    else
      invalidNel(CollectionsContainSameElementsAssertionError(added, deleted))
  }
}

case class CollectionsContainSameElementsAssertionError[A: Show](added: Seq[A], deleted: Seq[A]) extends CornichonError {
  val baseErrorMessage = s"""|Non ordered diff. between actual result and expected result is :
                             |${if (added.isEmpty) "" else "added elements:\n" + added.show}
                             |${if (deleted.isEmpty) "" else "deleted elements:\n" + deleted.show}
      """.stripMargin.trim
}
