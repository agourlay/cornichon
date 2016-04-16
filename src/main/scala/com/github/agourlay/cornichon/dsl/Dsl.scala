package com.github.agourlay.cornichon.dsl

import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.core.{ Scenario ⇒ ScenarioDef }
import com.github.agourlay.cornichon.dsl.CoreAssertion.{ SessionAssertion, SessionValuesAssertion }
import com.github.agourlay.cornichon.steps.regular._
import com.github.agourlay.cornichon.steps.wrapped._

import scala.language.experimental.{ macros ⇒ `scalac, please just let me do it!` }
import scala.concurrent.duration.{ Duration, FiniteDuration }

trait Dsl {

  def Feature(name: String, ignored: Boolean = false) =
    BodyElementCollector[Scenario, FeatureDef](scenarios ⇒ FeatureDef(name, scenarios, ignored))

  def Scenario(name: String, ignored: Boolean = false) =
    BodyElementCollector[Step, Scenario](steps ⇒ ScenarioDef(name, steps, ignored))

  sealed trait Starters {
    def name: String

    def I(steps: Seq[Step]) = steps

    def I(step: Step) = step

    def I[A](step: EffectStep) =
      step.copy(s"$name I ${step.title}")

    def a[A](step: EffectStep) =
      step.copy(s"$name a ${step.title}")

    def I(ds: DebugStep) = ds
  }

  case object When extends Starters { val name = "When" }
  case object Given extends Starters { val name = "Given" }

  sealed trait WithAssert {
    self: Starters ⇒

    def assert(steps: Seq[Step]) = steps

    def assert(step: Step) = step

    def assert[A](step: AssertStep[A]) =
      step.copy(s"$name assert ${step.title}")

    def assert_not[A](step: AssertStep[A]) =
      step.copy(s"$name assert not ${step.title}").copy(negate = true)
  }

  case object Then extends Starters with WithAssert { val name = "Then" }
  case object And extends Starters with WithAssert { val name = "And" }

  def Attach =
    BodyElementCollector[Step, Seq[Step]] { steps ⇒
      steps
    }

  def Repeat(times: Int) =
    BodyElementCollector[Step, Step] { steps ⇒
      RepeatStep(steps, times)
    }

  def RetryMax(limit: Int) =
    BodyElementCollector[Step, Step] { steps ⇒
      RetryMaxStep(steps, limit)
    }

  def RepeatDuring(duration: Duration) =
    BodyElementCollector[Step, Step] { steps ⇒
      RepeatDuringStep(steps, duration)
    }

  def Eventually(maxDuration: Duration, interval: Duration) =
    BodyElementCollector[Step, Step] { steps ⇒
      val conf = EventuallyConf(maxDuration, interval)
      EventuallyStep(steps, conf)
    }

  def Concurrently(factor: Int, maxTime: Duration) =
    BodyElementCollector[Step, Step] { steps ⇒
      ConcurrentlyStep(steps, factor, maxTime)
    }

  def Within(maxDuration: Duration) =
    BodyElementCollector[Step, Step] { steps ⇒
      WithinStep(steps, maxDuration)
    }

  def LogDuration(label: String) =
    BodyElementCollector[Step, Step] { steps ⇒
      LogDurationStep(steps, label)
    }

  def wait(duration: FiniteDuration) = EffectStep(
    title = s"wait for ${duration.toMillis} millis",
    effect = s ⇒ {
    Thread.sleep(duration.toMillis)
    s
  }
  )

  def save(input: (String, String)) = {
    val (key, value) = input
    EffectStep(
      s"add '$key'->'$value' to session",
      s ⇒ s.addValue(key, value)
    )
  }

  def remove(key: String) = EffectStep(
    title = s"remove '$key' from session",
    effect = s ⇒ s.removeKey(key)
  )

  def session_contains(input: (String, String)): AssertStep[String] = session_contains(input._1, input._2)

  def session_value(key: String) = SessionAssertion(key)

  def session_values(k1: String, k2: String) = SessionValuesAssertion(k1, k2)

  def session_contains(key: String, value: String) =
    AssertStep(
      title = s"session key '$key' equals '$value'",
      action = s ⇒ SimpleStepAssertion(value, s.get(key))
    )

  def show_session = DebugStep(s ⇒ s"Session content : \n${s.prettyPrint}")

  def show_session(key: String, transform: String ⇒ String = identity) = DebugStep(s ⇒ s"Session content for key '$key' is '${transform(s.get(key))}'")

  def print_step(message: String) = DebugStep(s ⇒ message)
}

object Dsl {

  case class FromSessionSetter(fromKey: String, trans: (Session, String) ⇒ String, target: String)

  def save_from_session(args: Seq[FromSessionSetter]) = {
    val keys = args.map(_.fromKey)
    val extractors = args.map(_.trans)
    val targets = args.map(_.target)
    EffectStep(
      s"save parts from session '${displayTuples(keys.zip(targets))}'",
      session ⇒ {
        val extracted = session.getList(keys).zip(extractors).map { case (value, extractor) ⇒ extractor(session, value) }
        targets.zip(extracted).foldLeft(session)((s, tuple) ⇒ s.addValue(tuple._1, tuple._2))
      }
    )
  }

  def displayTuples(params: Seq[(String, String)]): String = {
    params.map { case (name, value) ⇒ s"'$name' -> '$value'" }.mkString(", ")
  }

  def from_session_step[A](key: String, expected: Session ⇒ A, mapValue: (Session, String) ⇒ A, title: String) =
    AssertStep(
      title,
      s ⇒ SimpleStepAssertion(
        expected = expected(s),
        result = mapValue(s, s.get(key))
      )
    )

  def from_session_detail_step[A](key: String, expected: Session ⇒ A, mapValue: (Session, String) ⇒ (A, A ⇒ String), title: String) =
    AssertStep(
      title,
      s ⇒ {
        val (res, details) = mapValue(s, s.get(key))
        DetailedStepAssertion(
          expected = expected(s),
          result = res,
          details = details
        )
      }
    )
}

