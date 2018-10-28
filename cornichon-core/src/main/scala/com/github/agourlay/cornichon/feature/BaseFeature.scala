package com.github.agourlay.cornichon.feature

import java.util.concurrent.ConcurrentLinkedDeque

import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.resolver.{ Mapper, PlaceholderResolver }
import com.github.agourlay.cornichon.matchers.{ Matcher, MatcherResolver }
import monix.execution.Scheduler

import scala.annotation.tailrec
import scala.collection.mutable.ListBuffer
import scala.concurrent.Future

trait BaseFeature {

  protected[cornichon] val beforeFeature: ListBuffer[() ⇒ Unit] = ListBuffer.empty
  protected[cornichon] val afterFeature: ListBuffer[() ⇒ Unit] = ListBuffer.empty

  protected[cornichon] val beforeEachScenario: ListBuffer[Step] = ListBuffer.empty
  protected[cornichon] val afterEachScenario: ListBuffer[Step] = ListBuffer.empty

  private[cornichon] lazy val config = BaseFeature.config
  lazy val executeScenariosInParallel: Boolean = config.executeScenariosInParallel

  lazy val placeholderResolver = new PlaceholderResolver(registerExtractors)
  lazy val matcherResolver = new MatcherResolver(registerMatcher)

  // Convenient implicits for the custom DSL's
  implicit lazy val ec = Scheduler.Implicits.global

  def feature: FeatureDef

  def registerExtractors: Map[String, Mapper] = Map.empty

  def registerMatcher: List[Matcher] = Nil

  def beforeFeature(before: ⇒ Unit): Unit =
    beforeFeature += (() ⇒ before)

  def afterFeature(after: ⇒ Unit): Unit =
    (() ⇒ after) +=: afterFeature

  def beforeEachScenario(step: Step): Unit =
    beforeEachScenario += step

  def afterEachScenario(step: Step): Unit =
    step +=: afterEachScenario
}

// Protect and free resources
object BaseFeature {

  // if the config does not exist we use the default values
  lazy val config = pureconfig.loadConfigOrThrow[Option[Config]]("cornichon").getOrElse(Config())

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
