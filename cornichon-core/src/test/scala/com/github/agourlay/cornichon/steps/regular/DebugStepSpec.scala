package com.github.agourlay.cornichon.steps.regular

import com.github.agourlay.cornichon.core.{ CornichonError, Scenario, ScenarioRunner, Session }
import com.github.agourlay.cornichon.steps.StepUtilSpec
import com.github.agourlay.cornichon.util.ScenarioMatchers
import org.scalatest.{ AsyncWordSpec, Matchers }

import scala.util.control.NoStackTrace

class DebugStepSpec extends AsyncWordSpec with Matchers with StepUtilSpec with ScenarioMatchers {

  "DebugStep" must {
    "fail scenario if a Debug step throw an exception" in {
      val step = DebugStep("bad debug", _ => throw new RuntimeException("boom") with NoStackTrace)
      val s = Scenario("scenario with faulty debug step", step :: Nil)
      ScenarioRunner.runScenario(Session.newEmpty)(s).map { rep =>
        scenarioFailsWithMessage(rep) {
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

        matchLogsWithoutDuration(rep.logs) {
          """
            |   Scenario : scenario with faulty debug step
            |      main steps
            |      bad debug
            |      *** FAILED ***
            |      exception thrown com.github.agourlay.cornichon.steps.regular.DebugStepSpec$$anon$1: boom""".stripMargin
        }
      }
    }

    "fail scenario if a Debug step return an Either.Left" in {
      val step = DebugStep("invalid debug", _ => Left(CornichonError.fromString("debugging with println went wrong!")))
      val s = Scenario("scenario with faulty debug step", step :: Nil)
      ScenarioRunner.runScenario(Session.newEmpty)(s).map { rep =>
        scenarioFailsWithMessage(rep) {
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

        matchLogsWithoutDuration(rep.logs) {
          """
            |   Scenario : scenario with faulty debug step
            |      main steps
            |      invalid debug
            |      *** FAILED ***
            |      debugging with println went wrong!""".stripMargin
        }
      }
    }

    "debug info is present in the logs" in {
      val step = DebugStep("debug info", _ => Right("debugging with println"))
      val s = Scenario("scenario with correct debug step", step :: Nil)
      ScenarioRunner.runScenario(Session.newEmpty)(s).map { rep =>
        rep.isSuccess should be(true)
        matchLogsWithoutDuration(rep.logs) {
          """
            |   Scenario : scenario with correct debug step
            |      main steps
            |      debugging with println""".stripMargin
        }
      }
    }
  }
}
