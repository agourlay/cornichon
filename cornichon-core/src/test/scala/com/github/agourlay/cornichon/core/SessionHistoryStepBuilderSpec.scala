package com.github.agourlay.cornichon.core

import com.github.agourlay.cornichon.dsl.SessionSteps.SessionHistoryStepBuilder
import com.github.agourlay.cornichon.testHelpers.CommonTestSuite
import utest._

object SessionHistoryStepBuilderSpec extends TestSuite with CommonTestSuite {
  private val testKey = "test-key"
  private val sessionHistoryStepBuilder = SessionHistoryStepBuilder(testKey)

  val tests = Tests {
    test("SessionHistoryStepBuilder.containsExactly reports added elements") {
      val session = Session.newEmpty.addValuesUnsafe(testKey -> "test 1").addValuesUnsafe(testKey -> "test 2")
      val step = sessionHistoryStepBuilder.containsExactly("test 1")
      val s = Scenario("scenario with SessionHistoryStepBuilder", step :: Nil)
      val res = awaitTask(ScenarioRunner.runScenario(session)(s))
      scenarioFailsWithMessage(res) {
        """|Scenario 'scenario with SessionHistoryStepBuilder' failed:
           |
           |at step:
           |test-key history contains exactly
           |test 1
           |
           |with error(s):
           |Non ordered diff. between actual result and expected result is :
           |added elements:
           |test 2
           |
           |seed for the run was '1'
           |""".stripMargin
      }
    }

    test("SessionHistoryStepBuilder.containsExactly reports deleted elements") {
      val session = Session.newEmpty.addValuesUnsafe(testKey -> "test 1").addValuesUnsafe(testKey -> "test 2")
      val step = sessionHistoryStepBuilder.containsExactly("test 1", "test 2", "test 2")
      val s = Scenario("scenario with SessionHistoryStepBuilder", step :: Nil)
      val res = awaitTask(ScenarioRunner.runScenario(session)(s))
      scenarioFailsWithMessage(res) {
        """|Scenario 'scenario with SessionHistoryStepBuilder' failed:
           |
           |at step:
           |test-key history contains exactly
           |test 1 and test 2 and test 2
           |
           |with error(s):
           |Non ordered diff. between actual result and expected result is :
           |
           |deleted elements:
           |test 2
           |
           |seed for the run was '1'
           |""".stripMargin
      }
    }
  }
}
