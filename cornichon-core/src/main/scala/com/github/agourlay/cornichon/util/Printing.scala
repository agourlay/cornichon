package com.github.agourlay.cornichon.util

import cats.Show
import cats.syntax.show._

object Printing {
  private val arrow = " -> "
  def printArrowPairs(params: Seq[(String, String)]): String = {
    if (params.isEmpty) {
      return ""
    }
    val builder = new StringBuilder()
    printArrowPairsBuilder(params, builder)
    builder.result()
  }

  protected[cornichon] def printArrowPairsBuilder(params: Seq[(String, String)], builder: StringBuilder): Unit = {
    val len = params.length
    var i = 0
    params.foreach {
      case (name, value) =>
        builder.append("'")
        builder.append(name)
        builder.append("'")
        builder.append(arrow)
        builder.append("'")
        builder.append(value)
        builder.append("'")
        if (i < len - 1) {
          builder.append(", ")
        }
        i += 1
    }
  }

  implicit def showIterable[A: Show]: Show[Iterable[A]] = Show.show { fa =>
    fa.iterator.map(_.show).mkString("(", ", ", ")")
  }

  implicit def showMap[A: Show: Ordering, B: Show]: Show[Map[A, B]] = Show.show { ma =>
    ma.toSeq.sortBy(_._1).iterator.map(pair => pair._1.show + arrow + pair._2.show).mkString("\n")
  }
}
