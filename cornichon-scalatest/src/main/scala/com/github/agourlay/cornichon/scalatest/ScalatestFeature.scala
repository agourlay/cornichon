package com.github.agourlay.cornichon.scalatest

import java.util.concurrent.atomic.AtomicInteger

import com.github.agourlay.cornichon.core.LogInstruction._
import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.feature.{ BaseFeature, FeatureRunner }
import com.github.agourlay.cornichon.feature.BaseFeature.shutDownGlobalResources
import org.scalatest._
import com.github.agourlay.cornichon.scalatest.ScalatestFeature._
import monix.execution.Scheduler

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{ Failure, Success, Try }

trait ScalatestFeature extends AsyncWordSpecLike with BeforeAndAfterAll with ParallelTestExecution {
  this: BaseFeature ⇒

  override def beforeAll() = {
    reserveGlobalRuntime()
    beforeFeature.foreach(f ⇒ f())
  }

  override def afterAll() = {
    afterFeature.foreach(f ⇒ f())
    releaseGlobalRuntime()
  }

  override def run(testName: Option[String], args: Args) =
    if (executeScenariosInParallel) super.run(testName, args)
    else super.run(testName, args.copy(distributor = None))

  Try { feature } match {
    case Failure(e) ⇒
      "Cornichon" should {
        "bootstrap" in {
          val msg = e match {
            case c: CornichonError ⇒ c.renderedMessage
            case e: Throwable      ⇒ e.getMessage
          }
          fail(
            s"""
              |exception thrown during Feature initialization - $msg :
              |${CornichonError.genStacktrace(e)}
              |""".stripMargin
          )
        }
      }

    case Success(feat) ⇒
      feat.name should {
        feat.scenarios.foreach { s ⇒
          if (feat.ignored || s.ignored || (feat.focusedScenarios.nonEmpty && !feat.focusedScenarios.contains(s.name)))
            s.name ignore Future.successful(Succeeded)
          else if (s.pending)
            s.name in pending
          else
            s.name in {
              FeatureRunner(feature, this).runScenario(s).map {
                case s: SuccessScenarioReport ⇒
                  if (s.shouldShowLogs) printLogs(s.logs)
                  assert(true)
                case f: FailureScenarioReport ⇒
                  printLogs(f.logs)
                  fail(
                    s"""|${f.msg}
                        |${fansi.Color.Red("replay only this scenario with the command:").overlay(attrs = fansi.Underlined.On).render}
                        |${scalaTestReplayCmd(feat.name, s.name)}""".stripMargin
                  )
                case i: IgnoreScenarioReport ⇒
                  throw new RuntimeException(s"Scalatest filters ignored scenario upstream, this should never happen\n$i")
                case p: PendingScenarioReport ⇒
                  throw new RuntimeException(s"Scalatest filters pending scenario upstream, this should never happen\n$p")
              }.runAsync
            }
        }
      }
  }

  private def scalaTestReplayCmd(featureName: String, scenarioName: String) =
    s"""testOnly *${this.getClass.getSimpleName} -- -t "$featureName should $scenarioName" """

}

object ScalatestFeature {

  private val registeredUsage = new AtomicInteger
  private val safePassInRow = new AtomicInteger

  // Custom Reaper process for the time being as we want to cleanup afterall Feature
  // Will tear down stuff if no Feature registers during 10 secs
  Scheduler.Implicits.global.scheduleWithFixedDelay(5.seconds, 5.seconds) {
    if (registeredUsage.get() == 0) {
      safePassInRow.incrementAndGet()
      if (safePassInRow.get() == 2) shutDownGlobalResources()
    } else if (safePassInRow.get() > 0)
      safePassInRow.decrementAndGet()
  }

  def reserveGlobalRuntime(): Unit =
    registeredUsage.incrementAndGet()
  def releaseGlobalRuntime(): Unit =
    registeredUsage.decrementAndGet()
}
