package com.github.agourlay.cornichon.steps.regular.assertStep

import cats.Show
import cats.syntax.show._
import io.circe.Json
import io.circe.syntax._
import com.github.agourlay.cornichon.json.CornichonJson.diffPatch
import diffson.jsonpatch.JsonPatch
import diffson.circe._

trait Diff[A] {
  def diff(left: A, right: A): Option[String]
}

object Diff {
  def apply[A](implicit diff: Diff[A]): Diff[A] = diff

  implicit val showJsonPatch: Show[JsonPatch[Json]] = (jp: JsonPatch[Json]) => {
    jp.asJson.spaces2
  }

  implicit val jsonDiff: Diff[Json] = (left: Json, right: Json) => Some(
    s"""|JSON patch between actual result and expected result is :
        |${diffPatch(left, right).show}
      """.stripMargin.trim
  )

  implicit val stringDiff: Diff[String] = new Diff[String] {
    override def diff(left: String, right: String): Option[String] = None
  }

  implicit val booleanDiff: Diff[Boolean] = new Diff[Boolean] {
    override def diff(left: Boolean, right: Boolean): Option[String] = None
  }

  def orderedCollectionDiff[A: Show](left: Seq[A], right: Seq[A]): String = {
    val added = right.diff(left).iterator
    val (deletedTuple, stillPresent) = left.map(e => (e, right.indexWhere(_ == e))).partition(_._2 == -1)
    val deleted = deletedTuple.iterator.map(_._1)
    val moved = stillPresent.iterator.map { case (elem, newIndex) => MovedElement(elem, newIndex, left.indexWhere(_ == elem)) }.filter(_.changed)
    s"""|Ordered collection diff. between actual result and expected result is :
        |${if (added.isEmpty) "" else "added elements:\n" + added.map(_.show).mkString("\n")}
        |${if (deleted.isEmpty) "" else "deleted elements:\n" + deleted.map(_.show).mkString("\n")}
        |${if (moved.isEmpty) "" else "moved elements:\n" + moved.map(_.show).mkString("\n")}
      """.stripMargin.trim
  }

  def notOrderedCollectionDiff[A: Show](left: Set[A], right: Set[A]): String = {
    val added = right.diff(left)
    val deleted = left.diff(right)
    s"""|Not ordered collection diff. between actual result and expected result is :
        |${if (added.isEmpty) "" else "added elements:\n" + added.iterator.map(_.show).mkString("\n")}
        |${if (deleted.isEmpty) "" else "deleted elements:\n" + deleted.iterator.map(_.show).mkString("\n")}
      """.stripMargin.trim
  }

  implicit def seqDiff[A: Show]: Diff[Seq[A]] = (left: Seq[A], right: Seq[A]) => Some(orderedCollectionDiff(left, right))

  implicit def listDiff[A: Show]: Diff[List[A]] = (left: List[A], right: List[A]) => Some(orderedCollectionDiff(left, right))

  implicit def vectorDiff[A: Show]: Diff[Vector[A]] = (left: Vector[A], right: Vector[A]) => Some(orderedCollectionDiff(left, right))

  implicit def arrayDiff[A: Show]: Diff[Array[A]] = (left: Array[A], right: Array[A]) => Some(orderedCollectionDiff(left.toSeq, right.toSeq))

  implicit def immutableSetDiff[A: Show]: Diff[Set[A]] = (left: Set[A], right: Set[A]) => Some(notOrderedCollectionDiff(left, right))

  implicit val intDiff: Diff[Int] = new Diff[Int] {
    override def diff(left: Int, right: Int): Option[String] = None
  }

  implicit val doubleDiff: Diff[Double] = new Diff[Double] {
    override def diff(left: Double, right: Double): Option[String] = None
  }

  implicit val longDiff: Diff[Long] = new Diff[Long] {
    override def diff(left: Long, right: Long): Option[String] = None
  }

  implicit val intFloat: Diff[Float] = new Diff[Float] {
    override def diff(left: Float, right: Float): Option[String] = None
  }

}

private case class MovedElement[A](element: A, newIndex: Int, oldIndex: Int) {
  val changed = newIndex != oldIndex
}

private object MovedElement {
  implicit def showMoved[A: Show]: Show[MovedElement[A]] =
    Show.show { ma =>
      s"""from index ${ma.oldIndex} to index ${ma.newIndex}
         |${ma.element.show}"""
    }
}
