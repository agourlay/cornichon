package com.github.agourlay.cornichon.core

import com.github.agourlay.cornichon.CornichonFeature
import org.scalatest.{ ParallelTestExecution, WordSpecLike, BeforeAndAfterAll }
import scala.Console._

trait ScalaTestIntegration extends WordSpecLike with BeforeAndAfterAll with ParallelTestExecution with CornichonLogger {
  this: CornichonFeature ⇒

  override def beforeAll() = beforeFeature.foreach(f ⇒ f())
  override def afterAll() = afterFeature.foreach(f ⇒ f())

  feature.name should {

    feature.scenarios.foreach { s ⇒
      if (s.ignored)
        s.name ignore {}
      else
        s.name in {
          runScenario(s) match {
            case SuccessScenarioReport(scenarioName, successSteps: Seq[String], logs) ⇒
              if (s.steps.collect { case d @ DebugStep(_) ⇒ d }.nonEmpty)
                printLogs(logs)
              assert(true)
            case f @ FailedScenarioReport(scenarioName, failedStep, successSteps, notExecutedStep, logs) ⇒
              printLogs(logs)
              fail(f.msg)
          }
        }
    }
  }

  private def printLogs(logs: Seq[LogInstruction]): Unit = {
    logs.foreach {
      case DefaultLogInstruction(message)        ⇒ logger.info(message)
      case ColoredLogInstruction(message, color) ⇒ logger.info(color + message + RESET)
    }
  }
}
