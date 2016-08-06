package com.github.agourlay.cornichon.http

import cats.Show
import cats.syntax.show._

// Alias binding all typeclass
sealed abstract class BodyInput[I: Show: Resolvable] {

  def show(f: I): String
  def toResolvableForm(r: I): String
  def fromResolvableForm(r: String): I

}

object BodyInput extends BodyInputOps

trait BodyInputOps {

  implicit def default[I: Show: Resolvable] = new BodyInput[I] {
    def show(f: I): String = f.show
    def toResolvableForm(r: I): String = implicitly[Resolvable[I]].toResolvableForm(r)
    def fromResolvableForm(r: String): I = implicitly[Resolvable[I]].fromResolvableForm(r)
  }
}

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