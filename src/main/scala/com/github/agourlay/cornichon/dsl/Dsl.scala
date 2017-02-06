package com.github.agourlay.cornichon.dsl

import cats.Show
import com.github.agourlay.cornichon.CornichonFeature
import com.github.agourlay.cornichon.core.{ FeatureDef, Session, Step, Scenario ⇒ ScenarioDef }
import com.github.agourlay.cornichon.dsl.SessionSteps.SessionStepBuilder
import com.github.agourlay.cornichon.steps.regular._
import com.github.agourlay.cornichon.steps.wrapped._
import com.github.agourlay.cornichon.util.{ Instances, Futures }
import com.github.agourlay.cornichon.util.Instances._

import scala.annotation.unchecked.uncheckedVariance
import scala.concurrent.Future
import scala.language.experimental.{ macros ⇒ `scalac, please just let me do it!` }
import scala.language.{ dynamics, higherKinds }
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
      RepeatStep(steps, times, None)
    }

  def Repeat(times: Int, indice: String) =
    BodyElementCollector[Step, Step] { steps ⇒
      RepeatStep(steps, times, Some(indice))
    }

  def RepeatWith(elements: ContainerType[Any, Show]*)(indice: String) =
    BodyElementCollector[Step, Step] { steps ⇒
      RepeatWithStep(steps, elements.map(c ⇒ c.tci.show(c.element)), indice)
    }

  def RepeatFrom[A](elements: Iterable[ContainerType[A, Show]])(indice: String) =
    BodyElementCollector[Step, Step] { steps ⇒
      RepeatWithStep(steps, elements.toSeq.map(c ⇒ c.tci.show(c.element)), indice)
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

  def wait(duration: FiniteDuration) = EffectStep(
    title = s"wait for ${duration.toMillis} millis",
    effect = s ⇒ Futures.evalAfter(duration)(Future.successful(s))
  )

  def save(input: (String, String)) = {
    val (key, value) = input
    EffectStep.fromSync(
      s"add value '$value' to session under key '$key' ",
      s ⇒ {
        val resolved = resolver.fillPlaceholdersUnsafe(value)(s)
        s.addValue(key, resolved)
      }
    )
  }

  def remove(key: String) = EffectStep.fromSync(
    title = s"remove '$key' from session",
    effect = s ⇒ s.removeKey(key)
  )

  def session_value(key: String) = SessionStepBuilder(resolver, key)

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
    EffectStep.fromSync(
      s"save parts from session '${displayStringPairs(keys.zip(targets))}'",
      session ⇒ {
        val extracted = session.getList(keys).zip(extractors).map { case (value, extractor) ⇒ extractor(session, value) }
        targets.zip(extracted).foldLeft(session)((s, tuple) ⇒ s.addValue(tuple._1, tuple._2))
      }
    )
  }
}

case class ContainerType[+T, B[_]](element: T, tci: B[T @uncheckedVariance])
object ContainerType {
  implicit def showConv[T](a: T)(implicit tc: Show[T]): ContainerType[T, Show] = ContainerType(a, tc)
  implicit def showIterConv[T](a: Iterable[T])(implicit tc: Show[T]): Iterable[ContainerType[T, Show]] = a.map(ContainerType(_, tc))
}
