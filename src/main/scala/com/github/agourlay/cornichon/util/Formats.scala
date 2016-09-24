package com.github.agourlay.cornichon.util

import cats.Show
import cats.syntax.show._

object Formats {

  def displayTuples(params: Seq[(String, String)]): String =
    params.map { case (name, value) ⇒ s"'$name' -> '$value'" }.mkString(", ")

  def displayMap[A: Show](map: Map[String, A]): String =
    map.toSeq.sortBy(_._1).map(pair ⇒ pair._1 + " -> " + pair._2.show).mkString("\n")

}
