package com.github.agourlay.cornichon.dsl

import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.core.ExecutableStep._

import scala.Console._
import scala.concurrent.duration.Duration

trait Dsl extends CornichonLogger {

  private val resolver = new Resolver

  def Feature(name: String)(scenarios: Scenario*): FeatureDef = FeatureDef(name, scenarios.toVector)

  sealed trait Starters {
    val name: String
    def I[A](step: ExecutableStep[A])(implicit sb: ScenarioBuilder): ExecutableStep[A] = {
      val s: ExecutableStep[A] = step.copy(s"$name I ${step.title}")
      sb.addStep(s)
      s
    }

    def a[A](step: ExecutableStep[A])(implicit sb: ScenarioBuilder): ExecutableStep[A] = {
      val s: ExecutableStep[A] = step.copy(s"$name a ${step.title}")
      sb.addStep(s)
      s
    }

    def debug(s: DebugStep)(implicit sb: ScenarioBuilder): DebugStep = {
      sb.addStep(s)
      s
    }
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

    def assert_not[A](step: ExecutableStep[A])(implicit sb: ScenarioBuilder): ExecutableStep[A] = {
      val s: ExecutableStep[A] = step.copy(s"$name assert not ${step.title}").copy(negate = true)
      sb.addStep(s)
      s
    }
  }
  case object Then extends Starters with WithAssert { val name = "Then" }
  case object And extends Starters with WithAssert { val name = "And" }

  def Scenario(name: String, ignore: Boolean = false)(builder: ScenarioBuilder ⇒ Unit): Scenario = {
    val sb = new ScenarioBuilder()
    builder(sb)
    new Scenario(name, sb.steps.toVector, ignore)
  }

  def Repeat(times: Int)(steps: ⇒ Unit)(implicit b: ScenarioBuilder): Unit = {
    Seq.fill(times)(steps)
  }

  def Eventually(maxDuration: Duration, interval: Duration)(steps: ⇒ Unit)(implicit b: ScenarioBuilder) = {
    val conf = EventuallyConf(maxDuration, interval)
    b.addStep(EventuallyStart(conf))
    steps
    b.addStep(EventuallyStop(conf))
  }

  def Concurrently(factor: Int, maxTime: Duration)(steps: ⇒ Unit)(implicit b: ScenarioBuilder) = {
    b.addStep(ConcurrentStart(factor, maxTime))
    steps
    b.addStep(ConcurrentStop(factor))
  }

  def resolveInput[A](input: A): Session ⇒ A = s ⇒ input match {
    case string: String ⇒ resolver.fillPlaceholdersUnsafe(string)(s).asInstanceOf[A]
    case _              ⇒ input
  }

  def save(input: (String, String)): ExecutableStep[Boolean] = {
    val (key, value) = input
    effectStep(
      s"add '$key'->'$value' to session",
      s ⇒ s.addValue(key, value)
    )
  }

  def remove(key: String): ExecutableStep[Boolean] = {
    effectStep(
      s"remove '$key' from session",
      s ⇒ s.removeKey(key)
    )
  }

  def transform_assert_session[A](key: String, expected: Session ⇒ A, mapValue: (Session, String) ⇒ A, title: String) =
    ExecutableStep(
      title,
      s ⇒
        (s, SimpleStepAssertion(
          expected = expected(s),
          result = s.getOpt(key).fold(throw new KeyNotFoundInSession(key, s))(v ⇒ mapValue(s, v))
        ))
    )

  def save_from_session(key: String, extractor: String ⇒ String, target: String) =
    effectStep(
      s"save from session '$key' to '$target'",
      s ⇒ {
        val extracted = s.getOpt(key).fold(throw new KeyNotFoundInSession(key, s))(v ⇒ extractor(v))
        s.addValue(target, extracted)
      }
    )

  case class FromSessionSetter(fromKey: String, trans: String ⇒ String, target: String)

  def save_from_session(args: Seq[FromSessionSetter]) = {
    val keys = args.map(_.fromKey)
    val extractors = args.map(_.trans)
    val targets = args.map(_.target)
    effectStep(
      s"save parts from session '${displayTuples(keys.zip(targets))}'",
      s ⇒ {
        val extracted = s.getList(keys).zip(extractors).map { case (v, e) ⇒ e(v) }
        targets.zip(extracted).foldLeft(s)((s, tuple) ⇒ s.addValue(tuple._1, tuple._2))
      }
    )
  }

  def session_contains(input: (String, String)): ExecutableStep[String] = session_contains(input._1, input._2)

  def session_contains(key: String, value: String, title: Option[String] = None) =
    ExecutableStep(
      title = title.getOrElse(s"session '$key' equals '$value'"),
      action = s ⇒ {
        (s, SimpleStepAssertion(value, s.getOpt(key).fold(throw new KeyNotFoundInSession(key, s))(identity)))
      }
    )

  def show_session = DebugStep(s ⇒ s"Session content : \n${s.prettyPrint}")

  def show_session(key: String) =
    DebugStep { s ⇒
      val value = s.getOpt(key).fold(throw new KeyNotFoundInSession(key, s))(v ⇒ v)
      s"Session content for key '$key' is '$value'"
    }

  def log(msg: String): Unit = {
    msg.split('\n').foreach { m ⇒
      logger.info(CYAN + s"   $m" + RESET)
    }
  }

  def displayTuples(params: Seq[(String, String)]): String = {
    params.map { case (name, value) ⇒ s"$name -> $value" }.mkString(", ")
  }
}
