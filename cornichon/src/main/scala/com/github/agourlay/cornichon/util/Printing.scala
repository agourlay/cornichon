package com.github.agourlay.cornichon.util

object Printing {
  def displayStringPairs(params: Seq[(String, String)]): String =
    params.map { case (name, value) ⇒ s"'$name' -> '$value'" }.mkString(", ")
}
