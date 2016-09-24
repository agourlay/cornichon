package com.github.agourlay.cornichon.core

import com.github.agourlay.cornichon.CornichonFeature
import com.github.agourlay.cornichon.core.LogInstruction._
import org.scalatest.{ Args, BeforeAndAfterAll, ParallelTestExecution, WordSpecLike }

import scala.util.{ Failure, Success, Try }

trait ScalatestIntegration extends WordSpecLike with BeforeAndAfterAll with ParallelTestExecution {
  this: CornichonFeature ⇒

  override def beforeAll() = {
    registerFeature()
    beforeFeature.foreach(f ⇒ f())
  }

  override def afterAll() = {
    afterFeature.foreach(f ⇒ f())
    unregisterFeature()
  }

  override def run(testName: Option[String], args: Args) =
    if (config.executeScenariosInParallel) super.run(testName, args)
    else super.run(testName, args.copy(distributor = None))

  Try { feature } match {
    case Failure(e) ⇒
      "Cornichon" should {
        "bootstrap" in {
          val msg = e match {
            case c: CornichonError ⇒ c.msg
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
            s.name ignore {}
          else
            s.name in {
              runScenario(s) match {
                case SuccessScenarioReport(_, _, logs) ⇒
                  // In case of success, logs are only shown if the scenario contains DebugLogInstruction
                  if (logs.collect { case d: DebugLogInstruction ⇒ d }.nonEmpty) printLogs(logs)
                  assert(true)
                case f @ FailureScenarioReport(_, _, _, logs) ⇒
                  printLogs(logs)
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
