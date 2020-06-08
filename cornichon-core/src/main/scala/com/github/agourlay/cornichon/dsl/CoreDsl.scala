package com.github.agourlay.cornichon.dsl

import cats.Show
import cats.syntax.either._
import cats.syntax.show._
import cats.syntax.traverse._
import cats.instances.list._
import cats.instances.either._
import com.github.agourlay.cornichon.core.{ CornichonError, FeatureDef, ScenarioContext, Session, SessionKey, Step, Scenario => ScenarioDef }
import com.github.agourlay.cornichon.dsl.SessionSteps.{ SessionStepBuilder, SessionValuesStepBuilder }
import com.github.agourlay.cornichon.steps.cats.EffectStep
import com.github.agourlay.cornichon.steps.regular.DebugStep
import com.github.agourlay.cornichon.steps.wrapped._
import monix.eval.Task

import scala.annotation.unchecked.uncheckedVariance
import scala.language.{ dynamics, higherKinds }
import scala.concurrent.duration.FiniteDuration

trait CoreDsl extends ProvidedInstances {
  this: BaseFeature => //baseFeature brings the executionContext

  def Feature(name: String) = FeatureBuilder(name)

  private[dsl] case class FeatureBuilder(name: String, ignored: Option[String] = None) {
    def ignoredBecause(reason: String): FeatureBuilder = copy(ignored = Some(reason))
    def ignoredIfDefined(reason: Option[String]): FeatureBuilder = copy(ignored = reason)
  }

  implicit final def featureBuilder(f: FeatureBuilder): BodyElementCollector[ScenarioDef, FeatureDef] =
    BodyElementCollector[ScenarioDef, FeatureDef](scenarios => FeatureDef(f.name, scenarios, f.ignored))

  def Scenario(name: String) = ScenarioBuilder(name)

  private[dsl] case class ScenarioBuilder(name: String, ignored: Option[String] = None, focus: Boolean = false) {
    def ignoredBecause(reason: String): ScenarioBuilder = copy(ignored = Some(reason))
    def ignoredIfDefined(reason: Option[String]): ScenarioBuilder = copy(ignored = reason)
    /** Focus on this scenario ignoring all other scenarios withing a `Feature` */
    def focused: ScenarioBuilder = copy(focus = true)
    def pending = ScenarioDef(name, Nil, pending = true)
  }

  implicit final def scenarioBuilder(s: ScenarioBuilder): BodyElementCollector[Step, ScenarioDef] =
    BodyElementCollector[Step, ScenarioDef](steps => ScenarioDef(s.name, steps, s.ignored, focused = s.focus))

  sealed trait Starters extends Dynamic {
    def name: String
    def applyDynamic(mandatoryWord: String)(step: Step): Step = step.setTitle(s"$name $mandatoryWord ${step.title}")
  }

  case object When extends Starters { val name = "When" }
  case object Given extends Starters { val name = "Given" }
  case object Then extends Starters { val name = "Then" }
  case object And extends Starters { val name = "And" }

  def Attach: BodyElementCollector[Step, Step] =
    BodyElementCollector[Step, Step] { steps =>
      AttachStep(nested = _ => steps)
    }

  def AttachAs(title: String): BodyElementCollector[Step, Step] =
    BodyElementCollector[Step, Step] { steps =>
      AttachAsStep(title, _ => steps)
    }

  def Repeat(times: Int): BodyElementCollector[Step, Step] =
    BodyElementCollector[Step, Step] { steps =>
      RepeatStep(steps, times, None)
    }

  def Repeat(times: Int, index: String): BodyElementCollector[Step, Step] =
    BodyElementCollector[Step, Step] { steps =>
      RepeatStep(steps, times, Some(index))
    }

  def RepeatWith(elements: ContainerType[Any, Show]*)(index: String): BodyElementCollector[Step, Step] =
    BodyElementCollector[Step, Step] { steps =>
      RepeatWithStep(steps, elements.map(c => c.tci.show(c.element)).toList, index)
    }

  def RepeatFrom[A](elements: Iterable[ContainerType[A, Show]])(index: String): BodyElementCollector[Step, Step] =
    BodyElementCollector[Step, Step] { steps =>
      RepeatWithStep(steps, elements.map(c => c.tci.show(c.element)).toList, index)
    }

  def RetryMax(limit: Int): BodyElementCollector[Step, Step] =
    BodyElementCollector[Step, Step] { steps =>
      RetryMaxStep(steps, limit)
    }

  def RepeatDuring(duration: FiniteDuration): BodyElementCollector[Step, Step] =
    BodyElementCollector[Step, Step] { steps =>
      RepeatDuringStep(steps, duration)
    }

  def Eventually(maxDuration: FiniteDuration, interval: FiniteDuration, oscillationAllowed: Boolean = true): BodyElementCollector[Step, Step] =
    BodyElementCollector[Step, Step] { steps =>
      val conf = EventuallyConf(maxDuration, interval)
      EventuallyStep(steps, conf, oscillationAllowed)
    }

  def RepeatConcurrently(times: Int, parallelism: Int, maxTime: FiniteDuration): BodyElementCollector[Step, Step] =
    BodyElementCollector[Step, Step] { steps =>
      RepeatConcurrentlyStep(times, steps, parallelism, maxTime)
    }

  def Concurrently(maxTime: FiniteDuration): BodyElementCollector[Step, Step] =
    BodyElementCollector[Step, Step] { steps =>
      ConcurrentlyStep(steps, maxTime)
    }

  def Within(maxDuration: FiniteDuration): BodyElementCollector[Step, Step] =
    BodyElementCollector[Step, Step] { steps =>
      WithinStep(steps, maxDuration)
    }

  def LogDuration(label: String): BodyElementCollector[Step, Step] =
    BodyElementCollector[Step, Step] { steps =>
      LogDurationStep(steps, label)
    }

  def WithDataInputs(where: String): BodyElementCollector[Step, Step] =
    BodyElementCollector[Step, Step] { steps =>
      WithDataInputStep(steps, where)
    }

  def wait(duration: FiniteDuration): Step = EffectStep.fromAsync(
    title = s"wait for ${duration.toMillis} millis",
    effect = sc => Task.delay(sc.session).delayExecution(duration)
  )

  def save(input: (String, String), show: Boolean = true): Step = {
    val (key, value) = input
    EffectStep.fromSyncE(
      title = s"add value '$value' to session under key '$key'",
      effect = sc => {
        sc.fillPlaceholders(value).flatMap(sc.session.addValue(key, _))
      },
      show = show
    )
  }

  def remove(key: String): Step = EffectStep.fromSync(
    title = s"remove '$key' from session",
    effect = _.session.removeKey(key)
  )

  def rollback(key: String, show: Boolean = true): Step = EffectStep.fromSyncE(
    title = s"rollback '$key' in session",
    effect = _.session.rollbackKey(key),
    show = show
  )

  def transform_session(key: String)(map: String => String): Step = EffectStep.fromSyncE(
    title = s"transform '$key' from session",
    effect = sc => {
      for {
        v <- sc.session.get(key)
        tv <- Either.catchNonFatal(map(v)).leftMap(CornichonError.fromThrowable)
        ns <- sc.session.addValue(key, tv)
      } yield ns
    }
  )

  def session_value(key: String): SessionStepBuilder =
    SessionStepBuilder(key)

  def session_values(k1: String, k2: String): SessionValuesStepBuilder =
    SessionValuesStepBuilder(k1, k2)

  def show_session: Step =
    DebugStep("show session", sc => s"Session content is\n${sc.session.show}".asRight)

  def show_session(
    key: String,
    index: Option[Int] = None,
    transform: String => Either[CornichonError, String] = _.asRight): DebugStep =
    DebugStep(s"show session value for key $key", sc =>
      for {
        v <- sc.session.get(key, index)
        transformed <- transform(v)
      } yield s"Session content for key '${SessionKey(key, index).show}' is\n$transformed"
    )

  def print_step(message: String): Step =
    DebugStep("print step", _.fillPlaceholders(message))
}

object CoreDsl {

  case class FromSessionSetter(target: String, title: String, trans: (ScenarioContext, String) => Either[CornichonError, String])

  def save_many_from_session(fromKey: String)(args: Seq[FromSessionSetter]): Step =
    EffectStep.fromSyncE(
      s"${args.map(_.title).mkString(" and ")}",
      sc => {
        val session = sc.session
        for {
          sessionValue <- session.get(fromKey)
          extracted <- args.map(_.trans).toList.traverse { extractor => extractor(sc, sessionValue) }
          newSession <- args.map(_.target).zip(extracted).foldLeft(Either.right[CornichonError, Session](session))((s, tuple) => s.flatMap(_.addValue(tuple._1, tuple._2)))
        } yield newSession
      }
    )
}

case class ContainerType[+T, B[_]](element: T, tci: B[T @uncheckedVariance])
object ContainerType {
  implicit def showConv[T](a: T)(implicit tc: Show[T]): ContainerType[T, Show] = ContainerType(a, tc)
  implicit def showIterConv[T](a: Iterable[T])(implicit tc: Show[T]): Iterable[ContainerType[T, Show]] = a.map(ContainerType(_, tc))
}
