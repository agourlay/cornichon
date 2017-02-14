package com.github.agourlay.cornichon.scalatest

import com.github.agourlay.cornichon.core.LogInstruction._
import com.github.agourlay.cornichon.core.{ CornichonError, DebugLogInstruction, FailureScenarioReport, SuccessScenarioReport }
import com.github.agourlay.cornichon.feature.BaseFeature
import org.scalatest._

import scala.concurrent.Future
import scala.util.{ Failure, Success, Try }

trait ScalatestFeature extends AsyncWordSpecLike with BeforeAndAfterAll with ParallelTestExecution {
  this: BaseFeature ⇒

  override def beforeAll() = {
    registerFeature()
    beforeFeature.foreach(f ⇒ f())
  }

  override def afterAll() = {
    afterFeature.foreach(f ⇒ f())
    unregisterFeature()
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
          if (feat.ignored || s.ignored)
            s.name ignore { Future.successful(Succeeded) }
          else
            s.name in {
              runScenario(s).map {
                case s: SuccessScenarioReport ⇒
                  // In case of success, logs are only shown if the scenario contains DebugLogInstruction
                  if (s.logs.collect { case d: DebugLogInstruction ⇒ d }.nonEmpty) printLogs(s.logs)
                  assert(true)
                case f: FailureScenarioReport ⇒
                  printLogs(f.logs)
                  fail(
                    s"""|${f.msg}
                        |${fansi.Color.Red("replay only this scenario with the command:").overlay(attrs = fansi.Underlined.On).render}
                        |${scalaTestReplayCmd(feat.name, s.name)}""".stripMargin
                  )
              }
            }
        }
      }
  }

  private def scalaTestReplayCmd(featureName: String, scenarioName: String) =
    s"""testOnly *${this.getClass.getSimpleName} -- -t "$featureName should $scenarioName" """

}
