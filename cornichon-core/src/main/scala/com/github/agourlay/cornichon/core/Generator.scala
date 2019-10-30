package com.github.agourlay.cornichon.core

trait Generator[A] {
  def name: String
  def value(session: Session): () => A
}

trait NoValue
case object NoValue extends NoValue {
  val seededNoValueGenerator: RandomContext => Generator[NoValue] = _ => NoValueGenerator
}

case object NoValueGenerator extends Generator[NoValue] {
  val name = "NoValueGenerator"
  def value(session: Session): () => NoValue.type = () => NoValue
}

case class ValueGenerator[A](name: String, gen: () => A) extends Generator[A] {
  override def value(session: Session): () => A = gen
}

case class OptionalValueGenerator[A](name: String, gen: () => Option[A]) extends Generator[A] {
  override def value(session: Session): () => A = () => gen().fold(throw CornichonError.fromString(s"generator '$name' did not generate a value").toException)(identity)
}

case class SessionValueGenerator[A](name: String, gen: Session => A) extends Generator[A] {
  override def value(session: Session): () => A = () => gen(session)
}
