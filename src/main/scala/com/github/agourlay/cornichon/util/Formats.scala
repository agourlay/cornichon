package com.github.agourlay.cornichon.util

object Formats {

  def displayTuples(params: Seq[(String, String)]): String = {
    params.map { case (name, value) ⇒ s"'$name' -> '$value'" }.mkString(", ")
  }

}
