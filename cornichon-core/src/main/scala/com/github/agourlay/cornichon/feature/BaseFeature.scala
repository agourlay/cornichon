package com.github.agourlay.cornichon.feature

import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicInteger

import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.dsl.Dsl
import com.github.agourlay.cornichon.http.HttpDsl
import com.github.agourlay.cornichon.json.JsonDsl
import com.github.agourlay.cornichon.resolver.{ Mapper, PlaceholderResolver }
import com.github.agourlay.cornichon.feature.BaseFeature._
import com.github.agourlay.cornichon.matchers.{ Matcher, MatcherResolver }
import com.typesafe.config.ConfigFactory
import monix.execution.Scheduler

import scala.annotation.tailrec
import scala.concurrent.Future
import scala.concurrent.duration._

trait BaseFeature extends HttpDsl with JsonDsl with Dsl {

  private[cornichon] var beforeFeature: Seq[() ⇒ Unit] = Nil
  private[cornichon] var afterFeature: Seq[() ⇒ Unit] = Nil

  protected var beforeEachScenario: Seq[Step] = Nil
  protected var afterEachScenario: Seq[Step] = Nil

  implicit lazy val scheduler = globalScheduler

  private lazy val engine = Engine.withStepTitleResolver(placeholderResolver)

  private[cornichon] lazy val config = BaseFeature.config

  lazy val executeScenariosInParallel = config.executeScenariosInParallel

  lazy val placeholderResolver = new PlaceholderResolver(registerExtractors)
  lazy val matcherResolver = new MatcherResolver(registerMatcher)

  def runScenario(s: Scenario) = {
    val context = ScenarioExecutionContext(afterEachScenario.toList, feature.ignored, feature.focusedScenarios)

    println(s"Starting scenario '${s.name}'")

    engine.runScenario(Session.newEmpty, context) {
      s.copy(steps = beforeEachScenario.toList ++ s.steps)
    }
  }

  def feature: FeatureDef

  def registerExtractors: Map[String, Mapper] = Map.empty

  def registerMatcher: List[Matcher] = Nil

  def beforeFeature(before: ⇒ Unit): Unit =
    beforeFeature = beforeFeature :+ (() ⇒ before)

  def afterFeature(after: ⇒ Unit): Unit =
    afterFeature = (() ⇒ after) +: afterFeature

  def beforeEachScenario(step: Step): Unit =
    beforeEachScenario = beforeEachScenario :+ step

  def afterEachScenario(step: Step): Unit =
    afterEachScenario = step +: afterEachScenario
}

// Protect and free resources
object BaseFeature {
  import net.ceedubs.ficus.Ficus._
  import net.ceedubs.ficus.readers.ArbitraryTypeReader._

  lazy val config = ConfigFactory.load().as[Config]("cornichon")

  private val hooks = new ConcurrentLinkedDeque[() ⇒ Future[_]]()

  def addShutdownHook(h: () ⇒ Future[_]) =
    hooks.push(h)

  lazy val globalScheduler = Scheduler.Implicits.global

  def shutDownGlobalResources(): Future[Done] = {
    import scala.concurrent.ExecutionContext.Implicits.global
    @tailrec
    def clearHooks(previous: Future[Any] = Future.successful[Any](())): Future[Any] = {
      Option(hooks.poll()) match {
        case None ⇒ previous
        case Some(f) ⇒
          clearHooks {
            previous.flatMap { _ ⇒ f().recover { case _ ⇒ Done } }
          }
      }
    }

    clearHooks().map(_ ⇒ Done)
  }

  // Custom Reaper process for the time being
  // Will tear down stuff if no Feature registers during 10 secs
  private val reaperProcess = globalScheduler.scheduleWithFixedDelay(5.seconds, 5.seconds) {
    if (registeredUsage.get() == 0) {
      safePassInRow.incrementAndGet()
      if (safePassInRow.get() == 2) shutDownGlobalResources()
    } else if (safePassInRow.get() > 0)
      safePassInRow.decrementAndGet()
  }

  private val registeredUsage = new AtomicInteger
  private val safePassInRow = new AtomicInteger

  def disableAutomaticResourceCleanup() =
    reaperProcess.cancel()

  def reserveGlobalRuntime(): Unit =
    registeredUsage.incrementAndGet()
  def releaseGlobalRuntime(): Unit =
    registeredUsage.decrementAndGet()
}
