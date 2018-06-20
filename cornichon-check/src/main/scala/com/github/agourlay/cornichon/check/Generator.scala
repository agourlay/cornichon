package com.github.agourlay.cornichon.check

import com.github.agourlay.cornichon.core.Session

trait Generator[A] {
  def value(session: Session): () ⇒ A
  // FIXME: don't use context bound for now as it would make the type definition crazy in the engine
  def show(a: A): String = a.toString
}

case object NoValue

case object NoValueGenerator extends Generator[NoValue.type] {
  def value(session: Session) = () ⇒ NoValue

}

case class ValueGenerator[A](genFct: () ⇒ A) extends Generator[A] {
  override def value(session: Session) = genFct
}

case class ValueFromSessionGenerator[A](genFct: Session ⇒ A) extends Generator[A] {
  override def value(session: Session) = () ⇒ genFct(session)
}
