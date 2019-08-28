package com.github.agourlay.cornichon.util

import com.github.agourlay.cornichon.core.{ FailureScenarioReport, LogInstruction, ScenarioReport }
import org.scalatest.{ Assertion, AsyncWordSpecLike, Matchers }

trait ScenarioMatchers {
  this: AsyncWordSpecLike with Matchers ⇒

  def scenarioFailsWithMessage(report: ScenarioReport)(expectedMessage: String): Assertion =
    report match {
      case f: FailureScenarioReport ⇒
        withClue(f.msg + "\nwith logs\n" + LogInstruction.renderLogs(f.logs) + "\n\n") {
          f.msg should be(expectedMessage)
        }
      case other ⇒
        fail(s"Should have been a FailedScenarioReport but got \n${LogInstruction.renderLogs(other.logs)}")
    }

  def matchLogsWithoutDuration(logs: List[LogInstruction])(expectedRenderedLogs: String): Assertion = {
    val renderedLogs = LogInstruction.renderLogs(logs, colorized = false)
    val cleanedLogs = renderedLogs.split('\n').toList.map { l ⇒
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
    withClue(preparedCleanedLogs + "\n" + expectedRenderedLogs + "\n\n") {
      preparedCleanedLogs should be(expectedRenderedLogs)
    }
  }
}
