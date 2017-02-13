package com.github.agourlay.cornichon.steps.regular.assertStep

import cats.Show
import cats.instances.set._
import com.github.agourlay.cornichon.dsl.Instances._
import cats.syntax.show._
import io.circe.Json

trait Diff[A] {
  def diff(left: A, right: A): Option[String]
}

object Diff {
  def apply[A](implicit diff: Diff[A]): Diff[A] = diff

  implicit val jsonDiff = new Diff[Json] {
    def diff(left: Json, right: Json): Option[String] = Some(
      s"""|JSON patch between actual result and expected result is :
          |${com.github.agourlay.cornichon.json.CornichonJson.diffPatch(left, right).toString()}
      """.stripMargin.trim
    )
  }

  implicit val stringDiff = new Diff[String] {
    def diff(left: String, right: String): Option[String] = None
  }

  implicit val booleanDiff = new Diff[Boolean] {
    def diff(left: Boolean, right: Boolean): Option[String] = None
  }

  def orderedCollectionDiff[A: Show](left: Seq[A], right: Seq[A]) = {
    val added = right.diff(left)
    val (deletedTuple, stillPresent) = left.map(e ⇒ (e, right.indexWhere(_ == e))).partition(_._2 == -1)
    val deleted = deletedTuple.map(_._1)
    val moved = stillPresent.map { case (elem, newIndice) ⇒ MovedElement(elem, newIndice, left.indexWhere(_ == elem)) }.filter(_.changed)
    s"""|Ordered collection diff. between actual result and expected result is :
        |${if (added.isEmpty) "" else "added elements:\n" + added.show}
        |${if (deleted.isEmpty) "" else "deleted elements:\n" + deleted.show}
        |${if (moved.isEmpty) "" else "moved elements:\n" + moved.map(_.show).mkString("\n")}
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
      s"""|Set diff. between actual result and expected result is :
          |${if (added.isEmpty) "" else "added elements:\n" + added.show}
          |${if (deleted.isEmpty) "" else "deleted elements:\n" + deleted.show}
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

private case class MovedElement[A: Show](element: A, newIndice: Int, oldIndice: Int) {
  val changed = newIndice != oldIndice
}

private object MovedElement {
  implicit def showMoved[A: Show]: Show[MovedElement[A]] =
    Show.show { ma ⇒
      s"""from indice ${ma.oldIndice} to indice ${ma.newIndice}
         |${ma.element.show}"""
    }
}
