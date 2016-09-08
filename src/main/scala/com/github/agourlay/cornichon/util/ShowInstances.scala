package com.github.agourlay.cornichon.util

import java.util.UUID

import cats.Show
import cats.syntax.show._

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

  implicit def showList[A: Show]: Show[List[A]] = new Show[List[A]] {
    def show(fa: List[A]): String =
      fa.toIterator.map(_.show).mkString("List(", ", ", ")")
  }

  implicit def showSet[A: Show]: Show[Set[A]] = new Show[Set[A]] {
    def show(fa: Set[A]): String =
      fa.toIterator.map(_.show).mkString("Set(", ", ", ")")
  }

}

object ShowInstances extends ShowInstances
