package com.github.agourlay.cornichon.feature

import java.util.concurrent.{ Executors, ThreadFactory }
import java.util.concurrent.atomic.AtomicInteger

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer

import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.dsl.Dsl
import com.github.agourlay.cornichon.http.{ HttpDsl, HttpService }
import com.github.agourlay.cornichon.http.client.{ AkkaHttpClient, HttpClient }
import com.github.agourlay.cornichon.json.JsonDsl
import com.github.agourlay.cornichon.resolver.{ Mapper, Resolver }
import com.github.agourlay.cornichon.feature.BaseFeature._

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

  implicit lazy val (globalClient, _, _, scheduler) = globalRuntime
  private lazy val engine = Engine.withStepTitleResolver(resolver)

  private lazy val config = ConfigFactory.load().as[Config]("cornichon")
  lazy val requestTimeout = config.requestTimeout
  lazy val baseUrl = config.baseUrl
  lazy val executeScenariosInParallel = config.executeScenariosInParallel

  lazy val http = httpServiceByURL(baseUrl, requestTimeout)
  lazy val resolver = new Resolver(registerExtractors)

  def runScenario(s: Scenario) = {
    println(s"Starting scenario '${s.name}'")
    engine.runScenario(Session.newEmpty, afterEachScenario.toList, feature.ignored) {
      s.copy(steps = beforeEachScenario.toList ++ s.steps)
    }
  }

  def httpServiceByURL(baseUrl: String, timeout: FiniteDuration = requestTimeout) =
    new HttpService(baseUrl, timeout, globalClient, resolver)

  def feature: FeatureDef

  def registerExtractors: Map[String, Mapper] = Map.empty

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

  implicit private lazy val system = ActorSystem("cornichon-actor-system")
  implicit private lazy val mat = ActorMaterializer()

  private lazy val executorService = Executors.newScheduledThreadPool(
    Runtime.getRuntime.availableProcessors() + 1,
    new ThreadFactory {
      val count = new AtomicInteger(0)
      override def newThread(r: Runnable) = {
        new Thread(r, "cornichon-" + count.incrementAndGet)
      }
    }
  )

  implicit private lazy val scheduler: Scheduler = Scheduler(executorService)

  private lazy val client: HttpClient = new AkkaHttpClient()

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

  def shutDownGlobalResources(): Future[Unit] =
    for {
      _ ← client.shutdown()
      _ ← Future.successful(mat.shutdown())
      _ ← system.terminate()
    } yield executorService.shutdownNow()

  lazy val globalRuntime = (client, system, mat, scheduler)
  def reserveGlobalRuntime(): Unit = registeredUsage.incrementAndGet()
  def releaseGlobalRuntime(): Unit = registeredUsage.decrementAndGet()
}
