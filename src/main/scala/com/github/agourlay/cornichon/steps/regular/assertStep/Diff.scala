package com.github.agourlay.cornichon.steps.regular.assertStep

import cats.Show
import com.github.agourlay.cornichon.json.CornichonJson._
import com.github.agourlay.cornichon.util.ShowInstances._
import cats.syntax.show._
import io.circe.Json

trait Diff[A] {
  def diff(left: A, right: A): Option[String]
}

object Diff {
  def apply[A](implicit diff: Diff[A]): Diff[A] = diff

  implicit val jsonDiff = new Diff[Json] {
    def diff(left: Json, right: Json): Option[String] = Some(
      s"""|JSON diff. between actual result and expected result is :
          |${prettyDiff(left, right)}
      """.stripMargin.trim
    )
  }

  implicit val stringDiff = new Diff[String] {
    def diff(left: String, right: String): Option[String] = None
  }

  implicit val booleanDiff = new Diff[Boolean] {
    def diff(left: Boolean, right: Boolean): Option[String] = None
  }

  //TODO add info about elements out of order
  def orderedCollectionDiff[A: Show](left: Seq[A], right: Seq[A]) = {
    val added = left.diff(right)
    val deleted = right.diff(left)
    s"""|Ordered collection diff. between actual result and expected result is :
        |${if (added.nonEmpty) "" else "added = " + added.show}
        |${if (deleted.nonEmpty) "" else "deleted = " + deleted.show}
      """.stripMargin.trim
  }

  implicit def seqDiff[A: Show] = new Diff[Seq[A]] {
    def diff(left: Seq[A], right: Seq[A]): Option[String] = Some(orderedCollectionDiff(left, right))
  }

  implicit def listDiff[A: Show] = new Diff[List[A]] {
    def diff(left: List[A], right: List[A]): Option[String] = Some(orderedCollectionDiff(left, right))
  }

  implicit def vectorDiff[A: Show] = new Diff[Vector[A]] {
    def diff(left: Vector[A], right: Vector[A]): Option[String] = Some(orderedCollectionDiff(left, right))
  }

  implicit def arrayDiff[A: Show] = new Diff[Array[A]] {
    def diff(left: Array[A], right: Array[A]): Option[String] = Some(orderedCollectionDiff(left, right))
  }

  implicit def immutableSetDiff[A: Show] = new Diff[Set[A]] {
    def diff(left: Set[A], right: Set[A]): Option[String] = Some {
      val added = left.diff(right)
      val deleted = right.diff(left)
      s"""|Non ordered collection diff. between actual result and expected result is :
          |${if (added.nonEmpty) "" else "added = " + added.show}
          |${if (deleted.nonEmpty) "" else "deleted = " + deleted.show}
      """.stripMargin.trim
    }
  }

  implicit val intDiff = new Diff[Int] {
    def diff(left: Int, right: Int): Option[String] = None
  }

  implicit val doubleDiff = new Diff[Double] {
    def diff(left: Double, right: Double): Option[String] = None
  }

  implicit val longDiff = new Diff[Long] {
    def diff(left: Long, right: Long): Option[String] = None
  }

  implicit val intFloat = new Diff[Float] {
    def diff(left: Float, right: Float): Option[String] = None
  }

}
