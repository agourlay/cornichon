package com.github.agourlay.cornichon.steps.wrapped

import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.steps.regular.assertStep.{ AssertStep, GenericEqualityAssertion }
import com.github.agourlay.cornichon.testHelpers.CommonTestSuite
import utest._

object WithDataInputStepSpec extends TestSuite with CommonTestSuite {

  val tests = Tests {
    test("fails if table is malformed") {
      val nested = AssertStep(
        "always ok",
        _ => GenericEqualityAssertion(true, true)
      ) :: Nil
      val inputs =
        """
          | a | b | c |
          | 1 | 3  3 |
          | 7 | 4 | 4 |
          | 0  0 | 0 |
        """

      val withDataInputStep = WithDataInputStep(nested, inputs)
      val s = Scenario("scenario with WithDataInput", withDataInputStep :: Nil)
      val res = awaitTask(ScenarioRunner.runScenario(Session.newEmpty)(s))
      assert(!res.isSuccess)
    }

    test("fails at first failed input") {
      val nested = AssertStep(
        "always fails",
        _ => GenericEqualityAssertion(true, false)
      ) :: Nil
      val inputs =
        """
          | a | b | c |
          | 1 | 3 | 3 |
          | 7 | 4 | 4 |
          | 0 | 0 | 0 |
        """

      val withDataInputStep = WithDataInputStep(nested, inputs)
      val s = Scenario("scenario with WithDataInput", withDataInputStep :: Nil)
      val res = awaitTask(ScenarioRunner.runScenario(Session.newEmpty)(s))
      scenarioFailsWithMessage(res) {
        """|Scenario 'scenario with WithDataInput' failed:
           |
           |at step:
           |always fails
           |
           |with error(s):
           |WithDataInput block failed for inputs 'a' -> '1', 'b' -> '3', 'c' -> '3'
           |caused by:
           |expected result was:
           |'true'
           |but actual result is:
           |'false'
           |
           |seed for the run was '1'
           |""".stripMargin
      }
    }

    test("executes all steps if successful") {
      var uglyCounter = 0
      val nested = AssertStep(
        "always ok",
        _ => {
          uglyCounter = uglyCounter + 1
          GenericEqualityAssertion(true, true)
        }
      ) :: Nil
      val inputs =
        """
          | a | b | c |
          | 1 | 3 | 3 |
          | 7 | 4 | 4 |
          | 0 | 0 | 0 |
      """

      val withDataInputStep = WithDataInputStep(nested, inputs)
      val s = Scenario("scenario with WithDataInput", withDataInputStep :: Nil)
      val res = awaitTask(ScenarioRunner.runScenario(Session.newEmpty)(s))
      assert(res.isSuccess)
      assert(uglyCounter == 3)
    }

    test("injects values in session") {
      val nested = AssertStep(
        "sum of 'a' + 'b' = 'c'",
        sc => {
          val s = sc.session
          val sum = s.getUnsafe("a").toInt + s.getUnsafe("b").toInt
          GenericEqualityAssertion(sum, s.getUnsafe("c").toInt)
        }
      ) :: Nil
      val inputs =
        """
          | a | b  | c  |
          | 1 | 3  | 4  |
          | 7 | 4  | 11 |
          | 1 | -1 | 0  |
        """

      val withDataInputStep = WithDataInputStep(nested, inputs)
      val s = Scenario("scenario with WithDataInput", withDataInputStep :: Nil)
      val res = awaitTask(ScenarioRunner.runScenario(Session.newEmpty)(s))
      assert(res.isSuccess)
    }

    test("resolves placeholder") {
      val nested = AssertStep(
        "building URL",
        sc => {
          val s = sc.session
          val url = s.getUnsafe("endpoint") + "/" + s.getUnsafe("resource")
          GenericEqualityAssertion(url, s.getUnsafe("url"))
        }
      ) :: Nil
      val inputs =
        """
          | endpoint | resource   | url             |
          | "api"    | "products" | "api/products"  |
          | "api"    | "<other>"  | "api/customers" |
        """

      val withDataInputStep = WithDataInputStep(nested, inputs)
      val s = Scenario("scenario with WithDataInput", withDataInputStep :: Nil)
      val res = awaitTask(ScenarioRunner.runScenario(Session.newEmpty.addValueUnsafe("other", "customers"))(s))
      assert(res.isSuccess)
    }
  }
}
