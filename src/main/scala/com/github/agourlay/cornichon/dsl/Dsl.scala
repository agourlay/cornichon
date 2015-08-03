package com.github.agourlay.cornichon.dsl

import cats.data.Xor
import com.github.agourlay.cornichon.core._

trait Dsl {

  sealed trait Starters {
    def apply[Step[_]](step: Step[_]): Step[_] = step
    def apply[A](title: String)(instruction: Session ⇒ (A, Session))(assertion: A ⇒ Boolean): Step[A] =
      Step[A](title, instruction, assertion)
  }
  case object When extends Starters
  case object Then extends Starters
  case object And extends Starters
  case object Given extends Starters

  def scenario(name: String)(steps: Step[_]*): Scenario = Scenario(name, steps)

  def Xor2Predicate[A, B](input: Xor[A, B])(p: B ⇒ Boolean): Boolean =
    input.fold(a ⇒ false, b ⇒ p(b))

  def Set(key: String, value: String): Step[Boolean] =
    Step(s"add '$key'->'$value' to session",
      s ⇒ (true, s.addValue(key, value)), _ ⇒ true)

  def assertSession(key: String, value: String): Step[String] =
    Step(s"assert session '$key' with '$value'",
      s ⇒ {
        (s.getKey(key).fold(throw new KeyNotFoundInSession(key))(v ⇒ v), s)
      }, _ == value)

  def assertSession(key: String, p: String ⇒ Boolean): Step[String] =
    Step(s"assert '$key' against predicate",
      s ⇒ {
        (s.getKey(key).fold(throw new KeyNotFoundInSession(key))(v ⇒ v), s)
      }, p(_))

  def showSession: Step[Boolean] =
    Step(s"show session",
      s ⇒ {
        println(s.content)
        (true, s)
      }, _ ⇒ true)

  def showSession(key: String): Step[Boolean] =
    Step(s"show session",
      s ⇒ {
        val value = s.getKey(key).fold(throw new KeyNotFoundInSession(key))(v ⇒ v)
        println(value)
        (true, s)
      }, _ ⇒ true)
}
