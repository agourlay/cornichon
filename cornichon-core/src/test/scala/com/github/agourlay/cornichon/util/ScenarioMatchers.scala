package com.github.agourlay.cornichon.util

import com.github.agourlay.cornichon.core.{ FailureScenarioReport, LogInstruction, ScenarioReport }
import org.scalatest.{ Assertion, AsyncWordSpecLike, Matchers }

trait ScenarioMatchers {
  this: AsyncWordSpecLike with Matchers ⇒

  def scenarioFailsWithMessage(report: ScenarioReport)(expectedMessage: String): Assertion =
    report match {
      case f: FailureScenarioReport ⇒
        withClue(f.msg + "\nwith logs\n" + LogInstruction.renderLogs(f.logs)) {
          f.msg should be(expectedMessage)
        }
      case other ⇒
        fail(s"Should have been a FailedScenarioReport but got \n${LogInstruction.renderLogs(other.logs)}")
    }
}
