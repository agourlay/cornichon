package com.github.agourlay.cornichon.testHelpers

import com.github.agourlay.cornichon.core._
import munit.FunSuite

trait CommonTestSuite extends CommonTesting {
  this: FunSuite =>

  def scenarioFailsWithMessage(report: ScenarioReport)(expectedMessage: String): Unit =
    report match {
      case f: FailureScenarioReport =>
        def clue = f.msg + "\nwith logs\n" + LogInstruction.renderLogs(f.logs) + "\n\n"
        if (f.msg != expectedMessage) {
          println(s"diff\n${f.msg.toSeq.diff(expectedMessage)}|END")
          println(clue)
        }
        assert(f.msg == expectedMessage)
      case other =>
        fail(s"Should have been a FailedScenarioReport but got \n${LogInstruction.renderLogs(other.logs)}")
    }

  def matchLogsWithoutDuration(logs: List[LogInstruction])(expectedRenderedLogs: String): Unit = {
    val renderedLogs = LogInstruction.renderLogs(logs, colorized = false)
    val cleanedLogs = renderedLogs.split('\n').iterator.map { l =>
      // check if duration is present at end
      if (l.nonEmpty && l.last == ']')
        l.dropRight(1) // drop ']'
          .reverseIterator
          .dropWhile(_ != '[') // drop measurement
          .drop(1) // drop '['
          .dropWhile(_ == ' ') // drop whitespaces
          .toList
          .reverseIterator
          .mkString
      else l
    }
    val preparedCleanedLogs = cleanedLogs.mkString("\n")
    assertEquals(preparedCleanedLogs, expectedRenderedLogs)
  }
}
