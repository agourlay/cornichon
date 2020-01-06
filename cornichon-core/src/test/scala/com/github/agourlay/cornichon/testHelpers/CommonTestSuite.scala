package com.github.agourlay.cornichon.testHelpers

import com.github.agourlay.cornichon.core._
import utest._

trait CommonTestSuite extends CommonTesting {
  this: TestSuite =>

  def scenarioFailsWithMessage(report: ScenarioReport)(expectedMessage: String): Unit =
    report match {
      case f: FailureScenarioReport =>
        def clue = f.msg + "\nwith logs\n" + LogInstruction.renderLogs(f.logs) + "\n\n"
        if (f.msg != expectedMessage) {
          println(clue)
        }
        assert(f.msg == expectedMessage)
      case other =>
        println(s"Should have been a FailedScenarioReport but got \n${LogInstruction.renderLogs(other.logs)}")
        assert(false)
    }

  def matchLogsWithoutDuration(logs: List[LogInstruction])(expectedRenderedLogs: String): Unit = {
    val renderedLogs = LogInstruction.renderLogs(logs, colorized = false)
    val cleanedLogs = renderedLogs.split('\n').toList.map { l =>
      // check if duration is present at end
      if (l.nonEmpty && l.last == ']')
        l.dropRight(1) // drop ']'
          .reverse
          .dropWhile(_ != '[') // drop measurement
          .drop(1) // drop '['
          .dropWhile(_ == ' ') // drop whitespaces
          .reverse
      else
        l
    }
    val preparedCleanedLogs = cleanedLogs.mkString("\n")
    assert(preparedCleanedLogs == expectedRenderedLogs)
  }
}
