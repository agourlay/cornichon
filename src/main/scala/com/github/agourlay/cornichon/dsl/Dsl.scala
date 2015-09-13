package com.github.agourlay.cornichon.dsl

import com.github.agourlay.cornichon.core._

import scala.concurrent.duration.Duration

trait Dsl extends CornichonLogger {

  def Feature(name: String)(scenarios: Scenario*): FeatureDef = FeatureDef(name, scenarios)

  sealed trait Starters {
    val name: String
    def I[A](step: ExecutableStep[A])(implicit sb: ScenarioBuilder): ExecutableStep[A] = {
      val s: ExecutableStep[A] = step.copy(s"$name I ${step.title}")
      sb.addStep(s)
      s
    }
    def apply[A](title: String)(action: Session ⇒ (A, Session))(expected: A): ExecutableStep[A] = ExecutableStep[A](s"$name $title", action, expected)
  }
  case object When extends Starters { val name = "When" }
  case object Given extends Starters { val name = "Given" }

  sealed trait WithAssert {
    self: Starters ⇒
    def assert[A](step: ExecutableStep[A])(implicit sb: ScenarioBuilder): ExecutableStep[A] = {
      val s: ExecutableStep[A] = step.copy(s"$name assert ${step.title}")
      sb.addStep(s)
      s
    }
  }
  case object Then extends Starters with WithAssert { val name = "Then" }
  case object And extends Starters with WithAssert { val name = "And" }

  def Scenario(name: String)(builder: ScenarioBuilder ⇒ Unit): Scenario = {
    val sb = new ScenarioBuilder()
    builder(sb)
    new Scenario(name, sb.steps)
  }

  def Repeat(times: Int)(steps: ⇒ Unit)(implicit b: ScenarioBuilder): Unit = {
    Seq.fill(times)(steps)
  }

  def Eventually[A](maxDuration: Duration, interval: Duration)(steps: ⇒ Unit)(implicit b: ScenarioBuilder) = {
    val conf = EventuallyConf(maxDuration, interval)
    b.addStep(EventuallyStart(conf))
    steps
    b.addStep(EventuallyStop(conf))
  }

  def failWith[A](e: Exception, title: String, expected: A) = ExecutableStep(title, s ⇒ throw e, expected)

  def save(input: (String, String)): ExecutableStep[Boolean] = {
    val (key, value) = input
    ExecutableStep(
      s"add '$key'->'$value' to session",
      s ⇒ (true, s.addValue(key, value)), true
    )
  }

  def transform_assert_session[A](key: String, expected: A, mapValue: String ⇒ A, title: Option[String] = None) =
    ExecutableStep(
      title.getOrElse(s"session key '$key' against predicate"),
      s ⇒ (s.getKey(key).fold(throw new KeyNotFoundInSession(key))(v ⇒ mapValue(v)), s), expected
    )

  def extract_from_session(key: String, extractor: String ⇒ String, target: String) = {
    ExecutableStep(
      s"extract from session '$key' to '$target' using an extractor",
      s ⇒ {
        val extracted = s.getKey(key).fold(throw new KeyNotFoundInSession(key))(v ⇒ extractor(v))
        (true, s.addValue(target, extracted))
      }, true
    )
  }

  def session_contains(input: (String, String)): ExecutableStep[String] = session_contains(input._1, input._2)

  def session_contains(key: String, value: String, title: Option[String] = None) =
    ExecutableStep(
      title.getOrElse(s"session '$key' equals '$value'"),
      s ⇒ (s.getKey(key).fold(throw new KeyNotFoundInSession(key))(v ⇒ v), s), value
    )

  def show_session =
    ExecutableStep(
      s"show session",
      s ⇒ {
        log.info(s"Session content : \n${s.content.map(pair ⇒ pair._1 + " -> " + pair._2).mkString("\n")}")
        (true, s)
      }, true
    )

  def show_session(key: String) =
    ExecutableStep(
      s"show session key '$key'",
      s ⇒ {
        val value = s.getKey(key).fold(throw new KeyNotFoundInSession(key))(v ⇒ v)
        log.info(s"Session content for key '$key' is '$value'")
        (true, s)
      }, true
    )
}
