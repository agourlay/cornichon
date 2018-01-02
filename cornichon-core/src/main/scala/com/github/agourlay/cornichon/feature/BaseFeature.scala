package com.github.agourlay.cornichon.feature

import java.util.concurrent.ConcurrentLinkedDeque

import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.dsl.Dsl
import com.github.agourlay.cornichon.http.HttpDsl
import com.github.agourlay.cornichon.json.JsonDsl
import com.github.agourlay.cornichon.resolver.{ Mapper, PlaceholderResolver }
import com.github.agourlay.cornichon.matchers.{ Matcher, MatcherResolver }
import com.typesafe.config.ConfigFactory
import monix.execution.Scheduler

import scala.annotation.tailrec
import scala.concurrent.Future

trait CornichonBaseFeature extends Dsl {

  private[cornichon] var beforeFeature: Seq[() ⇒ Unit] = Nil
  private[cornichon] var afterFeature: Seq[() ⇒ Unit] = Nil

  protected var beforeEachScenario: List[Step] = Nil
  protected var afterEachScenario: List[Step] = Nil

  // Convenient implicits for the custom DSL's
  implicit lazy val ec = Scheduler.Implicits.global

  private lazy val engine = Engine.withStepTitleResolver(placeholderResolver)

  private[cornichon] lazy val config = CornichonBaseFeature.config

  lazy val executeScenariosInParallel = config.executeScenariosInParallel

  lazy val placeholderResolver = new PlaceholderResolver(registerExtractors)
  lazy val matcherResolver = new MatcherResolver(registerMatcher)

  private lazy val context = FeatureExecutionContext(beforeEachScenario, afterEachScenario, feature.ignored, feature.focusedScenarios)

  def runScenario(s: Scenario) = {
    println(s"Starting scenario '${s.name}'")
    engine.runScenario(Session.newEmpty, context)(s)
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
object CornichonBaseFeature {
  import net.ceedubs.ficus.Ficus._
  import net.ceedubs.ficus.readers.ArbitraryTypeReader._

  lazy val config = ConfigFactory.load().as[Config]("cornichon")

  private val hooks = new ConcurrentLinkedDeque[() ⇒ Future[_]]()

  def addShutdownHook(h: () ⇒ Future[_]) =
    hooks.push(h)

  def shutDownGlobalResources(): Future[Done] = {
    import scala.concurrent.ExecutionContext.Implicits.global
    @tailrec
    def clearHooks(previous: Future[Any] = Future.successful[Any](())): Future[Any] =
      Option(hooks.poll()) match {
        case None ⇒ previous
        case Some(f) ⇒
          clearHooks {
            previous.flatMap { _ ⇒ f().recover { case _ ⇒ Done } }
          }
      }

    clearHooks().map(_ ⇒ Done)
  }
}
