package com.github.agourlay.cornichon.core

import com.github.agourlay.cornichon.CornichonFeature
import org.scalatest.{ Suite, ParallelTestExecution, WordSpecLike, BeforeAndAfterAll }
import scala.Console._

trait ScalaTestIntegration extends WordSpecLike with BeforeAndAfterAll with ParallelTestExecution with CornichonLogger {
  this: CornichonFeature ⇒

  override def beforeAll() = beforeFeature.foreach(f ⇒ f())
  override def afterAll() = afterFeature.foreach(f ⇒ f())

  val featureDef = feature

  featureDef.name should {

    featureDef.scenarios.foreach { s ⇒
      if (s.ignored)
        s.name ignore {}
      else
        s.name in {
          runScenario(s) match {
            case SuccessScenarioReport(scenarioName, successSteps: Seq[String], logs) ⇒
              if (s.steps.collect { case d @ DebugStep(_) ⇒ d }.nonEmpty) printLogs(logs)
              assert(true)
            case f @ FailedScenarioReport(scenarioName, failedStep, successSteps, notExecutedStep, logs) ⇒
              printLogs(logs)
              fail(
                s"""
                   |${f.msg}
                   |replay only this scenario with:
                   |${replayCmd(featureDef.name, s.name)}
                   |""".stripMargin
              )
          }
        }
    }
  }

  private def replayCmd(featureName: String, scenarioName: String) =
    s"""testOnly *${this.getClass.getSimpleName} -- -t "$featureName should $scenarioName" """

  private def printLogs(logs: Seq[LogInstruction]): Unit = {
    def messageWithMargin(message: String, margin: Int): Array[String] = {
      message.split('\n').map { line ⇒
        "   " * margin + line
      }
    }

    logs.foreach {
      case DefaultLogInstruction(message, margin) ⇒
        messageWithMargin(message, margin).foreach(logger.info)
      case ColoredLogInstruction(message, color, margin) ⇒
        messageWithMargin(message, margin).foreach(l ⇒ logger.info(color + l + RESET))
    }
  }
}
