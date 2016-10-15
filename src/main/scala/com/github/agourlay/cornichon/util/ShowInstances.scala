package com.github.agourlay.cornichon.util

import java.util.UUID

import cats.Show
import cats.syntax.show._
import scala.collection.immutable.IndexedSeq

// Most of those instances are already present in Cats but we are trying to make it easier for potential non dev-users.
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

  implicit val showShort = new Show[Short] {
    def show(i: Short) = i.toString
  }

  implicit val showDouble = new Show[Double] {
    def show(i: Double) = i.toString
  }

  implicit val showFloat = new Show[Float] {
    def show(i: Float) = i.toString
  }

  implicit val showLong = new Show[Long] {
    def show(i: Long) = i.toString
  }

  implicit val showBigDec = new Show[BigDecimal] {
    def show(i: BigDecimal) = i.toString
  }

  implicit val showUUID = new Show[UUID] {
    def show(i: UUID) = i.toString
  }

  implicit def showOption[A: Show]: Show[Option[A]] = new Show[Option[A]] {
    def show(oa: Option[A]): String = oa.map(_.show).toString
  }

  implicit def showIterable[A: Show]: Show[Iterable[A]] = new Show[Iterable[A]] {
    def show(fa: Iterable[A]): String =
      fa.toIterator.map(_.show).mkString("Iterable(", ", ", ")")
  }

  implicit def showSeq[A: Show]: Show[Seq[A]] = new Show[Seq[A]] {
    def show(fa: Seq[A]): String =
      fa.toIterator.map(_.show).mkString("Seq(", ", ", ")")
  }

  implicit def showVector[A: Show]: Show[Vector[A]] = new Show[Vector[A]] {
    def show(fa: Vector[A]): String =
      fa.toIterator.map(_.show).mkString("Vector(", ", ", ")")
  }

  implicit def showArray[A: Show]: Show[Array[A]] = new Show[Array[A]] {
    def show(fa: Array[A]): String =
      fa.toIterator.map(_.show).mkString("Array(", ", ", ")")
  }

  implicit def showIndexedSeq[A: Show]: Show[IndexedSeq[A]] = new Show[IndexedSeq[A]] {
    def show(fa: IndexedSeq[A]): String =
      fa.toIterator.map(_.show).mkString("IndexedSeq(", ", ", ")")
  }

  implicit def showList[A: Show]: Show[List[A]] = new Show[List[A]] {
    def show(fa: List[A]): String =
      fa.toIterator.map(_.show).mkString("List(", ", ", ")")
  }

  implicit def showSet[A: Show]: Show[Set[A]] = new Show[Set[A]] {
    def show(fa: Set[A]): String =
      fa.toIterator.map(_.show).mkString("Set(", ", ", ")")
  }

  implicit def showMap[A: Show: Ordering, B: Show]: Show[Map[A, B]] = new Show[Map[A, B]] {
    def show(ma: Map[A, B]): String =
      ma.toSeq.sortBy(_._1).map(pair ⇒ pair._1.show + " -> " + pair._2.show).mkString("\n")
  }

  def displayStringPairs(params: Seq[(String, String)]): String =
    params.map { case (name, value) ⇒ s"'$name' -> '$value'" }.mkString(", ")

}

object ShowInstances extends ShowInstances
