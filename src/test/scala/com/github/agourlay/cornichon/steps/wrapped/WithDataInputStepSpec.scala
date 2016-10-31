package com.github.agourlay.cornichon.steps.wrapped

import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.steps.StepUtilSpec
import com.github.agourlay.cornichon.steps.regular.assertStep.{ AssertStep, GenericAssertion }
import org.scalatest.{ Matchers, AsyncWordSpec }

class WithDataInputStepSpec extends AsyncWordSpec with Matchers with StepUtilSpec {

  "WithDataInputStep" must {

    "fail if table is malformed" in {
      val nested = AssertStep(
        "always ok",
        s ⇒ GenericAssertion(true, true)
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
      val res = engine.runScenario(Session.newEmpty)(s)
      res.map(_.isSuccess should be(false))
    }

    "fail at first failed input" in {
      val nested = AssertStep(
        "always fails",
        s ⇒ GenericAssertion(true, false)
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
      engine.runScenario(Session.newEmpty)(s).map(_.isSuccess should be(false))
    }

    "execute all steps if successful" in {
      var uglyCounter = 0
      val nested = AssertStep(
        "always ok",
        s ⇒ {
          uglyCounter = uglyCounter + 1
          GenericAssertion(true, true)
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
      val res = engine.runScenario(Session.newEmpty)(s)
      res.map { res ⇒
        res.isSuccess should be(true)
        uglyCounter should be(3)
      }
    }

    "inject values in session" in {
      val nested = AssertStep(
        "sum of 'a' + 'b' = 'c'",
        s ⇒ {
          val sum = s.get("a").toInt + s.get("b").toInt
          GenericAssertion(sum, s.get("c").toInt)
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
      val res = engine.runScenario(Session.newEmpty)(s)
      res.map(_.isSuccess should be(true))
    }
  }

}
