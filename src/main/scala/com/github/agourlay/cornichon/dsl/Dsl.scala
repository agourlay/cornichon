package com.github.agourlay.cornichon.dsl

import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.core.{ Scenario ⇒ ScenarioDef }
import com.github.agourlay.cornichon.core.RunnableStep._

import scala.language.experimental.{ macros ⇒ `scalac, please just let me do it!` }

import scala.concurrent.duration.{ FiniteDuration, Duration }

trait Dsl extends CornichonLogger {
  def Feature(name: String) =
    BodyElementCollector[Scenario, FeatureDef](scenarios ⇒ FeatureDef(name, scenarios))

  def Scenario(name: String, ignored: Boolean = false) =
    BodyElementCollector[Step, Scenario](steps ⇒ ScenarioDef(name, steps, ignored))

  sealed trait Starters {
    val name: String

    def I[A](step: RunnableStep[A]) =
      step.copy(s"$name I ${step.title}")

    def a[A](step: RunnableStep[A]) =
      step.copy(s"$name a ${step.title}")

    def I(ds: DebugStep) = ds
  }

  case object When extends Starters { val name = "When" }
  case object Given extends Starters { val name = "Given" }

  sealed trait WithAssert {
    self: Starters ⇒

    def assert[A](step: RunnableStep[A]) =
      step.copy(s"$name assert ${step.title}")

    def assert_not[A](step: RunnableStep[A]) =
      step.copy(s"$name assert not ${step.title}").copy(negate = true)
  }

  case object Then extends Starters with WithAssert { val name = "Then" }
  case object And extends Starters with WithAssert { val name = "And" }

  def Repeat(times: Int) =
    BodyElementCollector[Step, Seq[Step]](steps ⇒ Seq.fill(times)(steps).flatten)

  def Eventually(maxDuration: Duration, interval: Duration) =
    BodyElementCollector[Step, Seq[Step]] { steps ⇒
      val conf = EventuallyConf(maxDuration, interval)

      EventuallyStart(conf) +: steps :+ EventuallyStop(conf)
    }

  def Concurrently(factor: Int, maxTime: Duration) =
    BodyElementCollector[Step, Seq[Step]](steps ⇒
      ConcurrentStart(factor, maxTime) +: steps :+ ConcurrentStop(factor))

  def wait(duration: FiniteDuration) = effectful(
    title = s"wait for ${duration.toMillis} millis",
    effect = s ⇒ {
    Thread.sleep(duration.toMillis)
    s
  }
  )

  def save(input: (String, String)): RunnableStep[Boolean] = {
    val (key, value) = input
    effectful(
      s"add '$key'->'$value' to session",
      s ⇒ s.addValue(key, value)
    )
  }

  def remove(key: String): RunnableStep[Boolean] = {
    effectful(
      s"remove '$key' from session",
      s ⇒ s.removeKey(key)
    )
  }

  def from_session_step[A](key: String, expected: Session ⇒ A, mapValue: (Session, String) ⇒ A, title: String) =
    RunnableStep(
      title,
      s ⇒
        (s, SimpleStepAssertion(
          expected = expected(s),
          result = mapValue(s, s.get(key))
        ))
    )

  def from_session_detail_step[A](key: String, expected: Session ⇒ A, mapValue: (Session, String) ⇒ (A, A ⇒ String), title: String) =
    RunnableStep(
      title,
      s ⇒ {
        val (res, details) = mapValue(s, s.get(key))
        (s, DetailedStepAssertion(
          expected = expected(s),
          result = res,
          details = details
        ))
      }
    )

  def save_from_session(key: String, extractor: String ⇒ String, target: String) =
    effectful(
      s"save from session '$key' to '$target'",
      s ⇒ s.addValue(target, extractor(s.get(key)))
    )

  case class FromSessionSetter(fromKey: String, trans: String ⇒ String, target: String)

  def save_from_session(args: Seq[FromSessionSetter]) = {
    val keys = args.map(_.fromKey)
    val extractors = args.map(_.trans)
    val targets = args.map(_.target)
    effectful(
      s"save parts from session '${displayTuples(keys.zip(targets))}'",
      s ⇒ {
        val extracted = s.getList(keys).zip(extractors).map { case (v, e) ⇒ e(v) }
        targets.zip(extracted).foldLeft(s)((s, tuple) ⇒ s.addValue(tuple._1, tuple._2))
      }
    )
  }

  def session_contains(input: (String, String)): RunnableStep[String] = session_contains(input._1, input._2)

  def session_contains(key: String, value: String, title: Option[String] = None) =
    RunnableStep(
      title = title.getOrElse(s"session '$key' equals '$value'"),
      action = s ⇒ (s, SimpleStepAssertion(value, s.get(key)))
    )

  def show_session = DebugStep(s ⇒ s"Session content : \n${s.prettyPrint}")

  def show_session(key: String, transform: String ⇒ String = identity) = DebugStep(s ⇒ s"Session content for key '$key' is '${transform(s.get(key))}'")

  def print_step(message: String) = DebugStep(s ⇒ message)

  def content_equality_for(k1: String, k2: String) = RunnableStep(
    title = s"content of key '$k1' is equal to content of key '$k2'",
    action = s ⇒ (s, SimpleStepAssertion(s.get(k1), s.get(k2)))
  )

  def displayTuples(params: Seq[(String, String)]): String = {
    params.map { case (name, value) ⇒ s"$name -> $value" }.mkString(", ")
  }
}
