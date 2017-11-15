package com.github.agourlay.cornichon.dsl

import cats.Show
import cats.syntax.either._
import cats.syntax.show._
import cats.syntax.traverse._
import cats.instances.list._
import cats.instances.either._
import com.github.agourlay.cornichon.core.{ CornichonError, FeatureDef, Session, Step, Scenario ⇒ ScenarioDef }
import com.github.agourlay.cornichon.dsl.SessionSteps.SessionStepBuilder
import com.github.agourlay.cornichon.feature.BaseFeature
import com.github.agourlay.cornichon.steps.regular._
import com.github.agourlay.cornichon.steps.wrapped._
import com.github.agourlay.cornichon.util.Printing._
import monix.eval.Task

import scala.annotation.unchecked.uncheckedVariance
import scala.collection.breakOut
import scala.language.experimental.{ macros ⇒ `scalac, please just let me do it!` }
import scala.language.{ dynamics, higherKinds }
import scala.concurrent.duration.FiniteDuration

trait Dsl extends ProvidedInstances {
  this: BaseFeature ⇒

  def Feature(name: String) = FeatureBuilder(name)

  private[dsl] case class FeatureBuilder(name: String, ignored: Boolean = false) {
    def ignoredBecause(reason: String) = copy(ignored = true)
  }

  implicit def featureBuilder(f: FeatureBuilder): BodyElementCollector[ScenarioDef, FeatureDef] =
    BodyElementCollector[ScenarioDef, FeatureDef](scenarios ⇒ FeatureDef(f.name, scenarios, f.ignored))

  def Scenario(name: String) = ScenarioBuilder(name)

  private[dsl] case class ScenarioBuilder(name: String, ignored: Boolean = false, focus: Boolean = false) {
    def ignoredBecause(reason: String) = copy(ignored = true)
    /** Focus on this scenario ignoring all other scenarios withing a `Feature` */
    def focused = copy(focus = true)
    def pending = ScenarioDef(name, Nil, pending = true)
  }

  implicit def scenarioBuilder(s: ScenarioBuilder): BodyElementCollector[Step, ScenarioDef] =
    BodyElementCollector[Step, ScenarioDef](steps ⇒ ScenarioDef(s.name, steps, s.ignored, focused = s.focus))

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
      RepeatStep(steps, times, None)
    }

  def Repeat(times: Int, indice: String) =
    BodyElementCollector[Step, Step] { steps ⇒
      RepeatStep(steps, times, Some(indice))
    }

  def RepeatWith(elements: ContainerType[Any, Show]*)(indice: String) =
    BodyElementCollector[Step, Step] { steps ⇒
      RepeatWithStep(steps, elements.map(c ⇒ c.tci.show(c.element))(breakOut), indice)
    }

  def RepeatFrom[A](elements: Iterable[ContainerType[A, Show]])(indice: String) =
    BodyElementCollector[Step, Step] { steps ⇒
      RepeatWithStep(steps, elements.map(c ⇒ c.tci.show(c.element))(breakOut), indice)
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
      WithDataInputStep(steps, where, placeholderResolver)
    }

  def wait(duration: FiniteDuration) = EffectStep.fromAsync(
    title = s"wait for ${duration.toMillis} millis",
    effect = s ⇒ Task.delay(s).delayExecution(duration).runAsync
  )

  def save(input: (String, String)) = {
    val (key, value) = input
    EffectStep.fromSyncE(
      s"add value '$value' to session under key '$key' ",
      s ⇒ placeholderResolver.fillPlaceholders(value)(s).flatMap(s.addValue(key, _))
    )
  }

  def remove(key: String) = EffectStep.fromSync(
    title = s"remove '$key' from session",
    effect = _.removeKey(key)
  )

  def rollback(key: String) = EffectStep.fromSyncE(
    title = s"rollback '$key' in session",
    effect = _.rollbackKey(key)
  )

  def transform_session(key: String)(map: String ⇒ String) = EffectStep.fromSyncE(
    title = s"transform '$key' from session",
    effect = s ⇒ {
      for {
        v ← s.get(key)
        tv ← Either.catchNonFatal(map(v)).leftMap(CornichonError.fromThrowable)
        ns ← s.addValue(key, tv)
      } yield ns
    }
  )

  def session_value(key: String) = SessionStepBuilder(placeholderResolver, matcherResolver, key)

  def show_session = DebugStep(s ⇒ s"Session content is\n${s.show}".asRight)

  def show_session(key: String, indice: Option[Int] = None, transform: String ⇒ String = identity) = DebugStep { s ⇒
    s.get(key, indice).map {
      v ⇒ s"Session content for key '$key${indice.map(i ⇒ s"[$i]").getOrElse("")}' is\n${transform(v)}"
    }
  }

  def print_step(message: String) = DebugStep(placeholderResolver.fillPlaceholders(message))
}

object Dsl {

  case class FromSessionSetter(fromKey: String, trans: (Session, String) ⇒ Either[CornichonError, String], target: String)

  def save_from_session(args: Seq[FromSessionSetter]) = {
    val keys = args.map(_.fromKey)
    val extractors = args.map(_.trans)
    val targets = args.map(_.target)
    EffectStep.fromSyncE(
      s"save parts from session '${printArrowPairs(keys.zip(targets))}'",
      session ⇒ {
        for {
          allValues ← session.getList(keys)
          extracted ← allValues.zip(extractors).traverseU { case (value, extractor) ⇒ extractor(session, value) }
          x ← targets.zip(extracted).foldLeft(Either.right[CornichonError, Session](session))((s, tuple) ⇒ s.flatMap(_.addValue(tuple._1, tuple._2)))
        } yield x
      }
    )
  }
}

case class ContainerType[+T, B[_]](element: T, tci: B[T @uncheckedVariance])
object ContainerType {
  implicit def showConv[T](a: T)(implicit tc: Show[T]): ContainerType[T, Show] = ContainerType(a, tc)
  implicit def showIterConv[T](a: Iterable[T])(implicit tc: Show[T]): Iterable[ContainerType[T, Show]] = a.map(ContainerType(_, tc))
}
