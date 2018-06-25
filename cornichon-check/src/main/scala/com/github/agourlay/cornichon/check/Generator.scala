package com.github.agourlay.cornichon.check

import java.util.concurrent.ConcurrentLinkedQueue

import com.github.agourlay.cornichon.core.Session

trait Generator[A] {
  def name: String

  def valueWithLog(logQueue: ConcurrentLinkedQueue[(String, String)], session: Session): () ⇒ A = () ⇒ {
    val generated = value(session)()
    logQueue.add((name, show(generated)))
    generated
  }

  def value(session: Session): () ⇒ A

  // FIXME: don't use context bound for now as it would make the type definition crazy in the engine
  def show(a: A): String = a.toString
}

trait NoValue
case object NoValue extends NoValue {
  val seededNoValueGenerator: RandomContext ⇒ Generator[NoValue] = _ ⇒ NoValueGenerator
}

case object NoValueGenerator extends Generator[NoValue] {
  val name = "NoValueGenerator"
  def value(session: Session) = () ⇒ NoValue
}

case class ValueGenerator[A](name: String, genFct: () ⇒ A) extends Generator[A] {
  override def value(session: Session) = genFct
}

case class ValueFromSessionGenerator[A](name: String, genFct: Session ⇒ A) extends Generator[A] {
  override def value(session: Session) = () ⇒ genFct(session)
}
