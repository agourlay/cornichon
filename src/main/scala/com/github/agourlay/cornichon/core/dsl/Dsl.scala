package com.github.agourlay.cornichon.core.dsl

import com.github.agourlay.cornichon.core.Feature.FeatureDef
import com.github.agourlay.cornichon.core._

import scala.language.higherKinds

trait Dsl {

  sealed trait Starters {
    def I[Step[_]](step: Step[_]): Step[_] = step
    def apply[A](title: String)(action: Session ⇒ (A, Session))(expected: A): Step[A] = Step[A](title, action, expected)
  }
  case object When extends Starters
  case object Then extends Starters {
    def assert[Step[_]](step: Step[_]): Step[_] = step
  }
  case object And extends Starters {
    def assert[Step[_]](step: Step[_]): Step[_] = step
  }
  case object Given extends Starters

  def Scenario(name: String)(steps: Step[_]*): Scenario = new Scenario(name, steps)

  def Feature(name: String)(scenarios: Scenario*): FeatureDef = FeatureDef(name, scenarios)

  def SET(input: (String, String)): Step[Boolean] = {
    val (key, value) = input
    Step(s"add '$key'->'$value' to session",
      s ⇒ (true, s.addValue(key, value)), true)
  }

  def transformAndAssertSession[A](key: String, expected: A, mapValue: String ⇒ A) =
    Step(s"assert session '$key' against predicate",
      s ⇒ (s.getKey(key).fold(throw new KeyNotFoundInSession(key))(v ⇒ mapValue(v)), s), expected)

  def extractFromSession(key: String, extractor: String ⇒ String, target: String) = {
    Step(s"extract from session '$key' to '$target' using an extractor",
      s ⇒ {
        val extracted = s.getKey(key).fold(throw new KeyNotFoundInSession(key))(v ⇒ extractor(v))
        (true, s.addValue(target, extracted))
      }, true)
  }

  def session_contain(input: (String, String)): Step[String] = session_contain(input._1, input._2)

  def session_contain(key: String, value: String) =
    Step(s"assert '$key' against predicate",
      s ⇒ (s.getKey(key).fold(throw new KeyNotFoundInSession(key))(v ⇒ v), s), value)

  def show_session =
    Step(s"show session",
      s ⇒ {
        println(s.content)
        (true, s)
      }, true)

  def show_session(key: String) =
    Step(s"show session",
      s ⇒ {
        val value = s.getKey(key).fold(throw new KeyNotFoundInSession(key))(v ⇒ v)
        println(value)
        (true, s)
      }, true)
}
