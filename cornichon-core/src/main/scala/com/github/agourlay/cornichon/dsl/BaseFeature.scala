package com.github.agourlay.cornichon.dsl

import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.matchers.Matcher
import com.github.agourlay.cornichon.resolver.Mapper

import java.util.concurrent.ConcurrentLinkedDeque

import pureconfig.error.{ ConvertFailure, KeyNotFound }

import scala.annotation.tailrec
import scala.collection.mutable.ListBuffer
import scala.concurrent.Future

trait BaseFeature {

  protected[cornichon] val beforeFeature: ListBuffer[() => Unit] = ListBuffer.empty
  protected[cornichon] val afterFeature: ListBuffer[() => Unit] = ListBuffer.empty

  protected[cornichon] val beforeEachScenario: ListBuffer[Step] = ListBuffer.empty
  protected[cornichon] val afterEachScenario: ListBuffer[Step] = ListBuffer.empty

  private[cornichon] lazy val config = BaseFeature.config
  lazy val executeScenariosInParallel: Boolean = config.executeScenariosInParallel

  lazy val seed: Option[Long] = None

  def feature: FeatureDef

  def registerExtractors: Map[String, Mapper] = Map.empty

  def registerMatchers: List[Matcher] = Nil

  def beforeFeature(before: => Unit): Unit =
    beforeFeature += (() => before)

  def afterFeature(after: => Unit): Unit =
    (() => after) +=: afterFeature

  def beforeEachScenario(step: Step): Unit =
    beforeEachScenario += step

  def afterEachScenario(step: Step): Unit =
    step +=: afterEachScenario
}

// Protect and free resources
object BaseFeature {
  import pureconfig.generic.auto._
  import pureconfig.ConfigSource
  import pureconfig.error.{ ConfigReaderException, ConfigReaderFailures }

  lazy val config = ConfigSource.default.at("cornichon").load[Config] match {
    case Right(v)                                                                          => v
    case Left(ConfigReaderFailures(ConvertFailure(KeyNotFound("cornichon", _), _, _), _*)) => Config()
    case Left(failures)                                                                    => throw new ConfigReaderException[Config](failures)
  }

  private val hooks = new ConcurrentLinkedDeque[() => Future[_]]()

  def addShutdownHook(h: () => Future[_]): Unit =
    hooks.push(h)

  def shutDownGlobalResources(): Future[Done] = {
    import scala.concurrent.ExecutionContext.Implicits.global
    @tailrec
    def clearHooks(previous: Future[Any] = Future.successful[Any](())): Future[Any] =
      Option(hooks.poll()) match {
        case None => previous
        case Some(f) =>
          clearHooks {
            previous.flatMap { _ => f().recover { case _ => Done } }
          }
      }

    clearHooks().map(_ => Done)
  }
}
