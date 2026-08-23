package com.github.agourlay.cornichon.steps.regular.assertStep

import com.github.agourlay.cornichon.core.{CornichonError, Scenario, ScenarioContext, ScenarioRunner, Session}
import com.github.agourlay.cornichon.testHelpers.CommonTestSuite
import munit.FunSuite

class GenericAssertStepBuilderSpec extends FunSuite with CommonTestSuite {

  private def builder(actual: Int, context: Option[() => String]): GenericAssertStepBuilder[Int] =
    new GenericAssertStepBuilder[Int] {
      protected val baseTitle: String = "the value"
      protected def sessionExtractor(sc: ScenarioContext): Either[CornichonError, (Int, Option[() => String])] =
        Right((actual, context))
    }

  private def runIs(actual: Int, expected: Int, context: Option[() => String]) = {
    val step = builder(actual, context).is(expected)
    awaitIO(ScenarioRunner.runScenario(Session.newEmpty)(Scenario("generic assert", step :: Nil)))
  }

  test("is succeeds when the values match") {
    assert(runIs(actual = 3, expected = 3, context = None).isSuccess)
    assert(runIs(actual = 3, expected = 3, context = Some(() => "ctx")).isSuccess)
  }

  // without extra context the failure goes through GenericEqualityAssertion, whose message
  // labels its two arguments - so they have to be passed as (expected, actual)
  test("is reports expected and actual the right way round") {
    scenarioFailsWithMessage(runIs(actual = 3, expected = 5, context = None)) {
      """|Scenario 'generic assert' failed:
         |
         |at step:
         |the value is '5'
         |
         |with error(s):
         |expected result was:
         |'5'
         |but actual result is:
         |'3'
         |
         |seed for the run was '1'
         |""".stripMargin
    }
  }

  test("is reports the custom message when the extractor supplies context") {
    scenarioFailsWithMessage(runIs(actual = 3, expected = 5, context = Some(() => "the surrounding array"))) {
      """|Scenario 'generic assert' failed:
         |
         |at step:
         |the value is '5'
         |
         |with error(s):
         |'3' was not equal to '5' for context
         |the surrounding array
         |
         |seed for the run was '1'
         |""".stripMargin
    }
  }

}
