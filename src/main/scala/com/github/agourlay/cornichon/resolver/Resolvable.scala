package com.github.agourlay.cornichon.resolver

trait Resolvable[A] {

  def toResolvableForm(r: A): String
  def fromResolvableForm(r: String): A
}

object Resolvable {

  implicit val stringResolvableForm = new Resolvable[String] {
    def toResolvableForm(s: String) = s
    def fromResolvableForm(s: String) = s
  }
}