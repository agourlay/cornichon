package com.github.agourlay.cornichon.feature

import java.util.concurrent.atomic.AtomicInteger

import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.dsl.Dsl
import com.github.agourlay.cornichon.http.{ HttpDsl, HttpService }
import com.github.agourlay.cornichon.http.client._
import com.github.agourlay.cornichon.json.JsonDsl
import com.github.agourlay.cornichon.resolver.{ Mapper, PlaceholderResolver }
import com.github.agourlay.cornichon.feature.BaseFeature._
import com.github.agourlay.cornichon.matchers.{ Matcher, MatcherResolver }
import com.typesafe.config.ConfigFactory
import monix.execution.Scheduler
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._

import scala.concurrent.Future
import scala.concurrent.duration._

trait BaseFeature extends HttpDsl with JsonDsl with Dsl {

  protected var beforeFeature: Seq[() ⇒ Unit] = Nil
  protected var afterFeature: Seq[() ⇒ Unit] = Nil

  protected var beforeEachScenario: Seq[Step] = Nil
  protected var afterEachScenario: Seq[Step] = Nil

  implicit lazy val (globalClient, scheduler) = globalRuntime
  private lazy val engine = Engine.withStepTitleResolver(placeholderResolver)

  private lazy val config = BaseFeature.config

  lazy val requestTimeout = config.requestTimeout
  lazy val baseUrl = config.baseUrl
  lazy val executeScenariosInParallel = config.executeScenariosInParallel

  lazy val http = httpServiceByURL(baseUrl, requestTimeout)
  lazy val placeholderResolver = new PlaceholderResolver(registerExtractors)
  lazy val matcherResolver = new MatcherResolver(registerMatcher)

  def runScenario(s: Scenario) = {
    println(s"Starting scenario '${s.name}'")
    engine.runScenario(Session.newEmpty, afterEachScenario.toList, feature.ignored) {
      s.copy(steps = beforeEachScenario.toList ++ s.steps)
    }
  }

  def httpServiceByURL(baseUrl: String, timeout: FiniteDuration = requestTimeout) =
    new HttpService(baseUrl, timeout, globalClient, placeholderResolver)

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

  private val scheduler = Scheduler.Implicits.global

  private val config = ConfigFactory.load().as[Config]("cornichon")

  private val client: HttpClient = new Http4sClient(config)

  private val registeredUsage = new AtomicInteger
  private val safePassInRow = new AtomicInteger

  // Custom Reaper process for the time being
  // Will tear down stuff if no Feature registers during 10 secs
  private val reaperProcess = scheduler.scheduleWithFixedDelay(5.seconds, 5.seconds) {
    if (registeredUsage.get() == 0) {
      safePassInRow.incrementAndGet()
      if (safePassInRow.get() == 2) shutDownGlobalResources()
    } else if (safePassInRow.get() > 0)
      safePassInRow.decrementAndGet()
  }

  def disableAutomaticResourceCleanup() =
    reaperProcess.cancel()

  def shutDownGlobalResources(): Future[Done] = client.shutdown()

  lazy val globalRuntime = (client, scheduler)
  def reserveGlobalRuntime(): Unit = registeredUsage.incrementAndGet()
  def releaseGlobalRuntime(): Unit = registeredUsage.decrementAndGet()
}
