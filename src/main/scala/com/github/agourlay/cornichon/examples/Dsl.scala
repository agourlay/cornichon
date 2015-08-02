package com.github.agourlay.cornichon.examples

import cats.data.Xor
import com.github.agourlay.cornichon.core._

trait Dsl {

  def scenario(name: String)(steps: Step[_]*): Scenario = Scenario(name, steps)

  def When(step: Step[_]) = step
  def When[A](title: String)(instruction: Session ⇒ (A, Session))(assertion: A ⇒ Boolean): Step[A] =
    Step[A](title, instruction, assertion)

  def Then(step: Step[_]) = step
  def Then[A](title: String)(instruction: Session ⇒ (A, Session))(assertion: A ⇒ Boolean): Step[A] =
    Step[A](title, instruction, assertion)

  def And(step: Step[_]) = step
  def And[A](title: String)(instruction: Session ⇒ (A, Session))(assertion: A ⇒ Boolean): Step[A] =
    Step[A](title, instruction, assertion)

  def Given(step: Step[_]) = step
  def Given[A](title: String)(instruction: Session ⇒ (A, Session))(assertion: A ⇒ Boolean): Step[A] =
    Step[A](title, instruction, assertion)

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
