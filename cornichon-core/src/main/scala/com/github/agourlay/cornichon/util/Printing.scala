package com.github.agourlay.cornichon.util

import cats.Show
import cats.syntax.show._

object Printing {
  def printArrowPairs(params: Seq[(String, String)]): String =
    params.map { case (name, value) ⇒ s"'$name' -> '$value'" }.mkString(", ")

  implicit def showIterable[A: Show]: Show[Iterable[A]] = Show.show { fa ⇒
    fa.toIterator.map(_.show).mkString("(", ", ", ")")
  }

  implicit def showMap[A: Show: Ordering, B: Show]: Show[Map[A, B]] = Show.show { ma ⇒
    ma.toSeq.sortBy(_._1).map(pair ⇒ pair._1.show + " -> " + pair._2.show).mkString("\n")
  }
}
