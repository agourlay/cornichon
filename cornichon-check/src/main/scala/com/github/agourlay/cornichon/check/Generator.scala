package com.github.agourlay.cornichon.check

import com.github.agourlay.cornichon.core.Session

trait Generator[A] {
  def name: String
  def value(session: Session): () ⇒ A
}

trait NoValue
case object NoValue extends NoValue {
  val seededNoValueGenerator: RandomContext ⇒ Generator[NoValue] = _ ⇒ NoValueGenerator
}

case object NoValueGenerator extends Generator[NoValue] {
  val name = "NoValueGenerator"
  def value(session: Session): () ⇒ NoValue.type = () ⇒ NoValue
}

case class ValueGenerator[A](name: String, genFct: () ⇒ A) extends Generator[A] {
  override def value(session: Session): () ⇒ A = genFct
}

case class ValueFromSessionGenerator[A](name: String, genFct: Session ⇒ A) extends Generator[A] {
  override def value(session: Session): () ⇒ A = () ⇒ genFct(session)
}
