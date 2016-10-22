package com.github.agourlay.cornichon.dsl

import cats.{ Eq, Show }
import com.github.agourlay.cornichon.CornichonFeature
import com.github.agourlay.cornichon.core.{ FeatureDef, Session, SessionKey, Step, Scenario ⇒ ScenarioDef }
import com.github.agourlay.cornichon.steps.regular._
import com.github.agourlay.cornichon.steps.regular.assertStep.{ AssertStep, CustomMessageAssertion, Diff, GenericAssertion }
import com.github.agourlay.cornichon.steps.wrapped._
import com.github.agourlay.cornichon.util.{ Instances, Timeouts }
import com.github.agourlay.cornichon.util.Instances._

import scala.language.experimental.{ macros ⇒ `scalac, please just let me do it!` }
import scala.language.dynamics
import scala.concurrent.duration.FiniteDuration

trait Dsl extends Instances {
  this: CornichonFeature ⇒

  def Feature(name: String, ignored: Boolean = false) =
    BodyElementCollector[ScenarioDef, FeatureDef](scenarios ⇒ FeatureDef(name, scenarios, ignored))

  def Scenario(name: String, ignored: Boolean = false) =
    BodyElementCollector[Step, ScenarioDef](steps ⇒ ScenarioDef(name, steps, ignored))

  sealed trait Starters extends Dynamic {
    def name: String

    def applyDynamic(mandatoryWord: String)(step: Step) = step.setTitle(s"$name $mandatoryWord ${step.title}")
  }

  case object When extends Starters { val name = "When" }
  case object Given extends Starters { val name = "Given" }
  case object Then extends Starters { val name = "Then" }
  case object And extends Starters { val name = "And" }

  def Attach: BodyElementCollector[Step, Step] =
    BodyElementCollector[Step, Step] { steps ⇒
      AttachStep(nested = steps)
    }

  def AttachAs(title: String) =
    BodyElementCollector[Step, Step] { steps ⇒
      AttachAsStep(title, steps)
    }

  def Repeat(times: Int) =
    BodyElementCollector[Step, Step] { steps ⇒
      RepeatStep(steps, times)
    }

  def RetryMax(limit: Int) =
    BodyElementCollector[Step, Step] { steps ⇒
      RetryMaxStep(steps, limit)
    }

  def RepeatDuring(duration: FiniteDuration) =
    BodyElementCollector[Step, Step] { steps ⇒
      RepeatDuringStep(steps, duration)
    }

  def Eventually(maxDuration: FiniteDuration, interval: FiniteDuration) =
    BodyElementCollector[Step, Step] { steps ⇒
      val conf = EventuallyConf(maxDuration, interval)
      EventuallyStep(steps, conf)
    }

  def Concurrently(factor: Int, maxTime: FiniteDuration) =
    BodyElementCollector[Step, Step] { steps ⇒
      ConcurrentlyStep(steps, factor, maxTime)
    }

  def Within(maxDuration: FiniteDuration) =
    BodyElementCollector[Step, Step] { steps ⇒
      WithinStep(steps, maxDuration)
    }

  def LogDuration(label: String) =
    BodyElementCollector[Step, Step] { steps ⇒
      LogDurationStep(steps, label)
    }

  def WithDataInputs(where: String) =
    BodyElementCollector[Step, Step] { steps ⇒
      WithDataInputStep(steps, where)
    }

  def wait(duration: FiniteDuration) = AsyncEffectStep(
    title = s"wait for ${duration.toMillis} millis",
    effect = s ⇒ Timeouts.timeout(duration)(s)
  )

  def save(input: (String, String)) = {
    val (key, value) = input
    EffectStep(
      s"add value '$value' to session under key '$key' ",
      s ⇒ {
        val resolved = resolver.fillPlaceholdersUnsafe(value)(s)
        s.addValue(key, resolved)
      }
    )
  }

  def remove(key: String) = EffectStep(
    title = s"remove '$key' from session",
    effect = s ⇒ s.removeKey(key)
  )

  def session_value(key: String) = SessionAssertion(resolver, key)

  def show_session = DebugStep(s ⇒ s"Session content is\n${s.prettyPrint}")

  def show_session(key: String, indice: Option[Int] = None, transform: String ⇒ String = identity) =
    DebugStep(s ⇒ s"Session content for key '$key${indice.map(i ⇒ s"[$i]").getOrElse("")}' is\n${transform(s.get(key, indice))}")

  def print_step(message: String) = DebugStep(_ ⇒ message)
}

object Dsl {

  case class FromSessionSetter(fromKey: String, trans: (Session, String) ⇒ String, target: String)

  def save_from_session(args: Seq[FromSessionSetter]) = {
    val keys = args.map(_.fromKey)
    val extractors = args.map(_.trans)
    val targets = args.map(_.target)
    EffectStep(
      s"save parts from session '${displayStringPairs(keys.zip(targets))}'",
      session ⇒ {
        val extracted = session.getList(keys).zip(extractors).map { case (value, extractor) ⇒ extractor(session, value) }
        targets.zip(extracted).foldLeft(session)((s, tuple) ⇒ s.addValue(tuple._1, tuple._2))
      }
    )
  }

  def from_session_step[A: Show: Diff: Eq](key: SessionKey, expected: Session ⇒ A, mapValue: (Session, String) ⇒ A, title: String) =
    AssertStep(
      title,
      s ⇒ GenericAssertion(
        expected = expected(s),
        actual = mapValue(s, s.get(key))
      )
    )

  def from_session_detail_step[A: Eq](key: SessionKey, expected: Session ⇒ A, mapValue: (Session, String) ⇒ (A, A ⇒ String), title: String) =
    AssertStep(
      title,
      s ⇒ {
        val (res, details) = mapValue(s, s.get(key))
        CustomMessageAssertion(
          expected = expected(s),
          actual = res,
          customMessage = details
        )
      }
    )
}

