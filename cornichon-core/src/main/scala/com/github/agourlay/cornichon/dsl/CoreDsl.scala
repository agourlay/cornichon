package com.github.agourlay.cornichon.dsl

import cats.Show
import cats.syntax.either._
import cats.syntax.show._
import cats.syntax.traverse._
import cats.instances.list._
import cats.instances.either._
import com.github.agourlay.cornichon.core.{ CornichonError, FeatureDef, Session, SessionKey, Step, Scenario ⇒ ScenarioDef }
import com.github.agourlay.cornichon.dsl.SessionSteps.{ SessionStepBuilder, SessionValuesStepBuilder }
import com.github.agourlay.cornichon.feature.BaseFeature
import com.github.agourlay.cornichon.steps.regular._
import com.github.agourlay.cornichon.steps.wrapped._
import monix.eval.Task

import scala.annotation.unchecked.uncheckedVariance
import scala.collection.breakOut
import scala.language.experimental.{ macros ⇒ `scalac, please just let me do it!` }
import scala.language.{ dynamics, higherKinds }
import scala.concurrent.duration.FiniteDuration

trait CoreDsl extends ProvidedInstances {
  this: BaseFeature ⇒

  def Feature(name: String) = FeatureBuilder(name)

  private[dsl] case class FeatureBuilder(name: String, ignored: Option[String] = None) {
    def ignoredBecause(reason: String): FeatureBuilder = copy(ignored = Some(reason))
    def ignoredIfDefined(reason: Option[String]): FeatureBuilder = copy(ignored = reason)
  }

  implicit final def featureBuilder(f: FeatureBuilder): BodyElementCollector[ScenarioDef, FeatureDef] =
    BodyElementCollector[ScenarioDef, FeatureDef](scenarios ⇒ FeatureDef(f.name, scenarios, f.ignored))

  def Scenario(name: String) = ScenarioBuilder(name)

  private[dsl] case class ScenarioBuilder(name: String, ignored: Option[String] = None, focus: Boolean = false) {
    def ignoredBecause(reason: String): ScenarioBuilder = copy(ignored = Some(reason))
    def ignoredIfDefined(reason: Option[String]): ScenarioBuilder = copy(ignored = reason)
    /** Focus on this scenario ignoring all other scenarios withing a `Feature` */
    def focused: ScenarioBuilder = copy(focus = true)
    def pending = ScenarioDef(name, Nil, pending = true)
  }

  implicit final def scenarioBuilder(s: ScenarioBuilder): BodyElementCollector[Step, ScenarioDef] =
    BodyElementCollector[Step, ScenarioDef](steps ⇒ ScenarioDef(s.name, steps, s.ignored, focused = s.focus))

  sealed trait Starters extends Dynamic {
    def name: String
    def applyDynamic(mandatoryWord: String)(step: Step): Step = step.setTitle(s"$name $mandatoryWord ${step.title}")
  }

  case object When extends Starters { val name = "When" }
  case object Given extends Starters { val name = "Given" }
  case object Then extends Starters { val name = "Then" }
  case object And extends Starters { val name = "And" }

  def Attach: BodyElementCollector[Step, Step] =
    BodyElementCollector[Step, Step] { steps ⇒
      AttachStep(nested = steps)
    }

  def AttachAs(title: String): BodyElementCollector[Step, Step] =
    BodyElementCollector[Step, Step] { steps ⇒
      AttachAsStep(title, steps)
    }

  def Repeat(times: Int): BodyElementCollector[Step, Step] =
    BodyElementCollector[Step, Step] { steps ⇒
      RepeatStep(steps, times, None)
    }

  def Repeat(times: Int, indice: String): BodyElementCollector[Step, Step] =
    BodyElementCollector[Step, Step] { steps ⇒
      RepeatStep(steps, times, Some(indice))
    }

  def RepeatWith(elements: ContainerType[Any, Show]*)(indice: String): BodyElementCollector[Step, Step] =
    BodyElementCollector[Step, Step] { steps ⇒
      RepeatWithStep(steps, elements.map(c ⇒ c.tci.show(c.element))(breakOut), indice)
    }

  def RepeatFrom[A](elements: Iterable[ContainerType[A, Show]])(indice: String): BodyElementCollector[Step, Step] =
    BodyElementCollector[Step, Step] { steps ⇒
      RepeatWithStep(steps, elements.map(c ⇒ c.tci.show(c.element))(breakOut), indice)
    }

  def RetryMax(limit: Int): BodyElementCollector[Step, Step] =
    BodyElementCollector[Step, Step] { steps ⇒
      RetryMaxStep(steps, limit)
    }

  def RepeatDuring(duration: FiniteDuration): BodyElementCollector[Step, Step] =
    BodyElementCollector[Step, Step] { steps ⇒
      RepeatDuringStep(steps, duration)
    }

  def Eventually(maxDuration: FiniteDuration, interval: FiniteDuration, oscillationAllowed: Boolean = true): BodyElementCollector[Step, Step] =
    BodyElementCollector[Step, Step] { steps ⇒
      val conf = EventuallyConf(maxDuration, interval)
      EventuallyStep(steps, conf, oscillationAllowed)
    }

  def RepeatConcurrently(times: Int, parallelism: Int, maxTime: FiniteDuration): BodyElementCollector[Step, Step] =
    BodyElementCollector[Step, Step] { steps ⇒
      RepeatConcurrentlyStep(times, steps, parallelism, maxTime)
    }

  def Concurrently(maxTime: FiniteDuration): BodyElementCollector[Step, Step] =
    BodyElementCollector[Step, Step] { steps ⇒
      ConcurrentlyStep(steps, maxTime)
    }

  def Within(maxDuration: FiniteDuration): BodyElementCollector[Step, Step] =
    BodyElementCollector[Step, Step] { steps ⇒
      WithinStep(steps, maxDuration)
    }

  def LogDuration(label: String): BodyElementCollector[Step, Step] =
    BodyElementCollector[Step, Step] { steps ⇒
      LogDurationStep(steps, label)
    }

  def WithDataInputs(where: String): BodyElementCollector[Step, Step] =
    BodyElementCollector[Step, Step] { steps ⇒
      WithDataInputStep(steps, where, placeholderResolver)
    }

  def wait(duration: FiniteDuration): Step = EffectStep.fromAsync(
    title = s"wait for ${duration.toMillis} millis",
    effect = s ⇒ Task.delay(s).delayExecution(duration).runToFuture
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

  def transform_session(key: String)(map: String ⇒ String): Step = EffectStep.fromSyncE(
    title = s"transform '$key' from session",
    effect = s ⇒ {
      for {
        v ← s.get(key)
        tv ← Either.catchNonFatal(map(v)).leftMap(CornichonError.fromThrowable)
        ns ← s.addValue(key, tv)
      } yield ns
    }
  )

  def session_value(key: String): SessionStepBuilder =
    SessionStepBuilder(placeholderResolver, matcherResolver, key)

  def session_values(k1: String, k2: String): SessionValuesStepBuilder =
    SessionValuesStepBuilder(placeholderResolver, k1, k2)

  def show_session: Step =
    DebugStep(s ⇒ s"Session content is\n${s.show}".asRight)

  def show_session(
    key: String,
    indice: Option[Int] = None,
    transform: String ⇒ Either[CornichonError, String] = _.asRight) =
    DebugStep { s ⇒
      for {
        v ← s.get(key, indice)
        transformed ← transform(v)
      } yield s"Session content for key '${SessionKey(key, indice).show}' is\n$transformed"
    }

  def print_step(message: String): Step =
    DebugStep(placeholderResolver.fillPlaceholders(message))
}

object CoreDsl {

  case class FromSessionSetter(fromKey: String, target: String, title: String, trans: (Session, String) ⇒ Either[CornichonError, String])

  def save_from_session(args: Seq[FromSessionSetter]): Step =
    EffectStep.fromSyncE(
      s"${args.map(_.title).mkString(" and ")}",
      session ⇒ {
        for {
          allValues ← session.getList(args.map(_.fromKey))
          extracted ← allValues.zip(args.map(_.trans)).traverse { case (value, extractor) ⇒ extractor(session, value) }
          newSession ← args.map(_.target).zip(extracted).foldLeft(Either.right[CornichonError, Session](session))((s, tuple) ⇒ s.flatMap(_.addValue(tuple._1, tuple._2)))
        } yield newSession
      }
    )
}

case class ContainerType[+T, B[_]](element: T, tci: B[T @uncheckedVariance])
object ContainerType {
  implicit def showConv[T](a: T)(implicit tc: Show[T]): ContainerType[T, Show] = ContainerType(a, tc)
  implicit def showIterConv[T](a: Iterable[T])(implicit tc: Show[T]): Iterable[ContainerType[T, Show]] = a.map(ContainerType(_, tc))
}
