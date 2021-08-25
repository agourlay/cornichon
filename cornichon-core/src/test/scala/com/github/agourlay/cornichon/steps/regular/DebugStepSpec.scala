package com.github.agourlay.cornichon.steps.regular

import com.github.agourlay.cornichon.core.{ CornichonError, Scenario, ScenarioRunner, Session }
import com.github.agourlay.cornichon.testHelpers.CommonTestSuite
import munit.FunSuite

import scala.util.control.NoStackTrace

class DebugStepSpec extends FunSuite with CommonTestSuite {

  test("fails scenario if a Debug step throws an exception") {
    val step = DebugStep("bad debug", _ => throw new RuntimeException("boom") with NoStackTrace)
    val s = Scenario("scenario with faulty debug step", step :: Nil)
    val res = awaitTask(ScenarioRunner.runScenario(Session.newEmpty)(s))
    scenarioFailsWithMessage(res) {
      """Scenario 'scenario with faulty debug step' failed:
          |
          |at step:
          |bad debug
          |
          |with error(s):
          |exception thrown com.github.agourlay.cornichon.steps.regular.DebugStepSpec$$anon$1: boom
          |
          |
          |seed for the run was '1'
          |""".stripMargin
    }

    matchLogsWithoutDuration(res.logs) {
      """
          |   Scenario : scenario with faulty debug step
          |      main steps
          |      bad debug
          |      *** FAILED ***
          |      exception thrown com.github.agourlay.cornichon.steps.regular.DebugStepSpec$$anon$1: boom""".stripMargin
    }
  }

  test("fails scenario if a Debug step returns an Either.Left") {
    val step = DebugStep("invalid debug", _ => Left(CornichonError.fromString("debugging with println went wrong!")))
    val s = Scenario("scenario with faulty debug step", step :: Nil)
    val res = awaitTask(ScenarioRunner.runScenario(Session.newEmpty)(s))
    scenarioFailsWithMessage(res) {
      """Scenario 'scenario with faulty debug step' failed:
          |
          |at step:
          |invalid debug
          |
          |with error(s):
          |debugging with println went wrong!
          |
          |seed for the run was '1'
          |""".stripMargin
    }

    matchLogsWithoutDuration(res.logs) {
      """
          |   Scenario : scenario with faulty debug step
          |      main steps
          |      invalid debug
          |      *** FAILED ***
          |      debugging with println went wrong!""".stripMargin
    }
  }

  test("debug info is present in the logs") {
    val step = DebugStep("debug info", _ => Right("debugging with println"))
    val s = Scenario("scenario with correct debug step", step :: Nil)
    val res = awaitTask(ScenarioRunner.runScenario(Session.newEmpty)(s))
    assert(res.isSuccess)
    matchLogsWithoutDuration(res.logs) {
      """
        |   Scenario : scenario with correct debug step
        |      main steps
        |      debugging with println""".stripMargin
    }
  }
}
