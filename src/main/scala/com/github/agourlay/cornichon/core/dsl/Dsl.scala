package com.github.agourlay.cornichon.core.dsl

import com.github.agourlay.cornichon.core.Feature.FeatureDef
import com.github.agourlay.cornichon.core._

trait Dsl {
  this: Feature ⇒

  sealed trait Starters {
    val name: String
    def I[A](step: Step[A]): Step[A] = step.copy(s"$name I ${step.title}")
    def apply[A](title: String)(action: Session ⇒ (A, Session))(expected: A): Step[A] = Step[A](s"$name $title", action, expected)
  }
  case object When extends Starters { val name = "When" }
  case object Given extends Starters { val name = "Given" }

  sealed trait WithAssert {
    self: Starters ⇒
    def assert[A](step: Step[A]): Step[A] = step.copy(s"$name assert ${step.title}")
  }
  case object Then extends Starters with WithAssert { val name = "Then" }
  case object And extends Starters with WithAssert { val name = "And" }

  def Scenario(name: String)(steps: Step[_]*): Scenario = new Scenario(name, steps)

  def Feature(name: String)(scenarios: Scenario*): FeatureDef = FeatureDef(name, scenarios)

  def save(input: (String, String)): Step[Boolean] = {
    val (key, value) = input
    Step(s"add '$key'->'$value' to session",
      s ⇒ (true, s.addValue(key, value)), true)
  }

  def transform_assert_session[A](key: String, expected: A, mapValue: String ⇒ A) =
    Step(s"session key '$key' against predicate",
      s ⇒ (s.getKey(key).fold(throw new KeyNotFoundInSession(key))(v ⇒ mapValue(v)), s), expected)

  def extract_from_session(key: String, extractor: String ⇒ String, target: String) = {
    Step(s"extract from session '$key' to '$target' using an extractor",
      s ⇒ {
        val extracted = s.getKey(key).fold(throw new KeyNotFoundInSession(key))(v ⇒ extractor(v))
        (true, s.addValue(target, extracted))
      }, true)
  }

  def session_contains(input: (String, String)): Step[String] = session_contains(input._1, input._2)

  def session_contains(key: String, value: String) =
    Step(s"session '$key' equals '$value'",
      s ⇒ (s.getKey(key).fold(throw new KeyNotFoundInSession(key))(v ⇒ v), s), value)

  def show_session =
    Step(s"show session",
      s ⇒ {
        log.info(s"Session content is \n '${s.content}}'")
        (true, s)
      }, true)

  def show_session(key: String) =
    Step(s"show session",
      s ⇒ {
        val value = s.getKey(key).fold(throw new KeyNotFoundInSession(key))(v ⇒ v)
        log.info(s"Session content for key $key is \n '$value'")
        (true, s)
      }, true)
}
