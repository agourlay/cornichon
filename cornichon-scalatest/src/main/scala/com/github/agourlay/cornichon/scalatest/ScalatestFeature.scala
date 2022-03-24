package com.github.agourlay.cornichon.scalatest

import com.github.agourlay.cornichon
import com.github.agourlay.cornichon.core.LogInstruction._
import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.dsl.BaseFeature
import com.github.agourlay.cornichon.dsl.BaseFeature.shutDownGlobalResources
import com.github.agourlay.cornichon.scalatest.ScalatestFeature._

import org.scalatest._
import org.scalatest.wordspec.AsyncWordSpecLike

import java.util
import java.util.concurrent.atomic.AtomicInteger

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{ Failure, Success, Try }

trait ScalatestFeature extends AsyncWordSpecLike with BeforeAndAfterAll with ParallelTestExecution {
  this: BaseFeature =>

  override def beforeAll() = {
    reserveGlobalRuntime()
    beforeFeature.foreach(f => f())
  }

  override def afterAll() = {
    afterFeature.foreach(f => f())
    releaseGlobalRuntime()
  }

  override def run(testName: Option[String], args: Args) =
    if (executeScenariosInParallel) super.run(testName, args)
    else super.run(testName, args.copy(distributor = None))

  Try { feature } match {
    case Failure(e) =>
      "Cornichon" should {
        "load feature definition" in {
          val msg = e match {
            case c: CornichonError => c.renderedMessage
            case e: Throwable      => e.getMessage
          }
          fail(
            s"""
              |exception thrown during Feature initialization - $msg :
              |${CornichonError.genStacktrace(e)}
              |""".stripMargin
          )
        }
      }

    case Success(feat) =>
      s"${feat.name}${feat.ignored.fold("")(r => s" ignored because $r")}" should {
        feat.scenarios.foreach { s =>
          if (s.ignored.isDefined)
            s"${s.name}${s.ignored.fold("")(r => s" ($r)")}" ignore Future.successful(Succeeded)
          else if (feat.ignored.isDefined)
            s"${s.name}${feat.ignored.fold("")(_ => s" (feature ignored)")}" ignore Future.successful(Succeeded)
          else if (feat.focusedScenarios.nonEmpty && !feat.focusedScenarios.contains(s.name))
            s"${s.name} (no focus)" ignore Future.successful(Succeeded)
          else if (s.pending)
            s.name in pending
          else
            s.name in {
              // No explicit seed in `cornichon-scalatest`
              cornichon.core.FeatureRunner(feature, this, explicitSeed = None).runScenario(s).map {
                case s: SuccessScenarioReport =>
                  if (s.shouldShowLogs) printLogs(s.logs)
                  assert(true)
                case f: FailureScenarioReport =>
                  printLogs(f.logs)
                  fail(
                    s"""|${f.msg}
                        |${fansi.Color.Red("replay only this scenario with the command:").overlay(attrs = fansi.Underlined.On).render}
                        |${scalaTestReplayCmd(feat.name, s.name)}""".stripMargin
                  )
                case i: IgnoreScenarioReport =>
                  throw new RuntimeException(s"Scalatest filters ignored scenario upstream, this should never happen\n$i")
                case p: PendingScenarioReport =>
                  throw new RuntimeException(s"Scalatest filters pending scenario upstream, this should never happen\n$p")
              }.unsafeToFuture()(cats.effect.unsafe.implicits.global)
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

  // Custom Reaper process for the time being as we want to cleanup after all Feature
  // Will tear down stuff if no Feature registers during 10 secs
  private val timer = new util.Timer()
  private val timerTask = new util.TimerTask {
    def run(): Unit = {
      if (registeredUsage.get() == 0) {
        safePassInRow.incrementAndGet()
        if (safePassInRow.get() == 2) { shutDownGlobalResources(); () } else ()
      } else if (safePassInRow.get() > 0) {
        safePassInRow.decrementAndGet()
        ()
      }
    }
  }
  timer.scheduleAtFixedRate(timerTask, 5.seconds.toMillis, 5.seconds.toMillis)

  def reserveGlobalRuntime(): Unit = {
    registeredUsage.incrementAndGet()
    ()
  }

  def releaseGlobalRuntime(): Unit = {
    registeredUsage.decrementAndGet()
    ()
  }
}
