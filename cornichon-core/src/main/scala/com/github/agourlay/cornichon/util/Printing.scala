package com.github.agourlay.cornichon.util

import cats.Show
import cats.syntax.show._

object Printing {
  def printArrowPairs(params: Seq[(String, String)]): String = {
    val len = params.length
    if (len == 0) {
      return ""
    }
    // custom mkString for performance
    val builder = new StringBuilder(len * 16)
    var i = 0
    params.foreach {
      case (name, value) =>
        if (i < len - 1) {
          builder.append(s"'$name' -> '$value', ")
        } else {
          builder.append(s"'$name' -> '$value'")
        }
        i += 1
    }
    builder.result()
  }

  implicit def showIterable[A: Show]: Show[Iterable[A]] = Show.show { fa =>
    fa.iterator.map(_.show).mkString("(", ", ", ")")
  }

  implicit def showMap[A: Show: Ordering, B: Show]: Show[Map[A, B]] = Show.show { ma =>
    ma.toSeq.sortBy(_._1).iterator.map(pair => pair._1.show + " -> " + pair._2.show).mkString("\n")
  }
}
