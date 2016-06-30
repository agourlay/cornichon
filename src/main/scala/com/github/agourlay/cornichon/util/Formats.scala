package com.github.agourlay.cornichon.util

object Formats {

  def displayTuples(params: Seq[(String, String)]): String =
    params.map { case (name, value) ⇒ s"'$name' -> '$value'" }.mkString(", ")

  val defaultShow = (elm: Any) ⇒ elm.toString

  def displayMap[A](map: Map[String, A], show: A ⇒ String = defaultShow): String =
    map.toSeq.sortBy(_._1).map(pair ⇒ pair._1 + " -> " + show(pair._2)).mkString("\n")

}
