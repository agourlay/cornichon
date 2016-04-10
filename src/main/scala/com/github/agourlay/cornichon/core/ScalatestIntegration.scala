package com.github.agourlay.cornichon.core

import com.github.agourlay.cornichon.CornichonFeature
import com.github.agourlay.cornichon.steps.regular.DebugStep
import org.scalatest.{ BeforeAndAfterAll, ParallelTestExecution, WordSpecLike }

import scala.Console._
import scala.util.{ Failure, Success, Try }

trait ScalatestIntegration extends WordSpecLike with BeforeAndAfterAll with ParallelTestExecution with CornichonLogger {
  this: CornichonFeature ⇒

  override def beforeAll() = {
    registerFeature()
    beforeFeature.foreach(f ⇒ f())
  }

  override def afterAll() = {
    afterFeature.foreach(f ⇒ f())
    unregisterFeature()
  }

  Try { feature } match {
    case Failure(e) ⇒
      "Cornichon" should {
        "bootstrap" in {
          val msg = e match {
            case c: CornichonError ⇒ c.msg
            case e: Throwable      ⇒ e.getMessage
          }
          fail(s"exception thrown during Feature initialization: \n $msg")
        }
      }
    case Success(feat) ⇒
      feat.name should {
        feat.scenarios.foreach { s ⇒
          if (feat.ignored || s.ignored)
            s.name ignore {}
          else
            s.name in {
              val scenarioReport = runScenario(s)
              scenarioReport.stepsRunReport match {
                case SuccessRunSteps(newSession, logs) ⇒
                  // In case of success, logs are only shown if the scenario contains Debug step(s)
                  if (s.steps.collect { case d: DebugStep ⇒ d }.nonEmpty) printLogs(logs)
                  assert(true)
                case FailedRunSteps(_, _, logs, _) ⇒
                  printLogs(logs)
                  fail(
                    s"""
                       |${scenarioReport.msg}
                       |replay only this scenario with:
                       |${replayCmd(feat.name, s.name)}
                       |""".stripMargin
                  )
              }
            }
        }
      }

  }

  private def replayCmd(featureName: String, scenarioName: String) =
    s"""testOnly *${this.getClass.getSimpleName} -- -t "$featureName should $scenarioName" """

  private def printLogs(logs: Seq[LogInstruction]): Unit = {
    logs.foreach { log ⇒
      val printableMsg = log.message.split('\n').map { line ⇒ "   " * log.margin + line }
      val durationSuffix = log.duration.fold("")(d ⇒ s" (${d.toMillis} millis)")
      logger.info(log.color + printableMsg.head + durationSuffix + RESET)
      printableMsg.tail.foreach(line ⇒ logger.info(log.color + line + RESET))
    }
  }
}
