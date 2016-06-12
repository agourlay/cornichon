package com.github.agourlay.cornichon.steps.wrapped

import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.steps.regular.AssertStep
import org.scalatest.{ Matchers, WordSpec }

import scala.concurrent.ExecutionContext

class WithDataInputStepSpec extends WordSpec with Matchers {

  val engine = new Engine(ExecutionContext.global)

  "WithDataInputStep" must {

    "fail if table is malformed" in {
      val nested: Vector[Step] = Vector(
        AssertStep(
          "always ok",
          s ⇒ SimpleStepAssertion(true, true)
        )
      )
      val inputs =
        """
          | a | b | c |
          | 1 | 3  3 |
          | 7 | 4 | 4 |
          | 0  0 | 0 |
        """

      val steps = Vector(
        WithDataInputStep(nested, inputs)
      )
      val s = Scenario("scenario with WithDataInput", steps)
      val res = engine.runScenario(Session.newSession)(s)
      res.isSuccess should be(false)
    }

    "fail at first failed input" in {
      val nested: Vector[Step] = Vector(
        AssertStep(
          "always fails",
          s ⇒ SimpleStepAssertion(true, false)
        )
      )
      val inputs =
        """
          | a | b | c |
          | 1 | 3 | 3 |
          | 7 | 4 | 4 |
          | 0 | 0 | 0 |
        """

      val steps = Vector(
        WithDataInputStep(nested, inputs)
      )
      val s = Scenario("scenario with WithDataInput", steps)
      engine.runScenario(Session.newSession)(s).isSuccess should be(false)
    }

    "execute all steps if successful" in {
      var uglyCounter = 0
      val nested: Vector[Step] = Vector(
        AssertStep(
          "always ok",
          s ⇒ {
            uglyCounter = uglyCounter + 1
            SimpleStepAssertion(true, true)
          }
        )
      )
      val inputs =
        """
        | a | b | c |
        | 1 | 3 | 3 |
        | 7 | 4 | 4 |
        | 0 | 0 | 0 |
      """

      val steps = Vector(
        WithDataInputStep(nested, inputs)
      )
      val s = Scenario("scenario with WithDataInput", steps)
      val res = engine.runScenario(Session.newSession)(s)
      res.isSuccess should be(true)
      uglyCounter should be(3)
    }

    "inject values in session" in {
      val nested: Vector[Step] = Vector(
        AssertStep(
          "sum of 'a' + 'b' = 'c'",
          s ⇒ {
            val sum = s.get("a").toInt + s.get("b").toInt
            SimpleStepAssertion(sum, s.get("c").toInt)
          }
        )
      )
      val inputs =
        """
          | a | b  | c  |
          | 1 | 3  | 4  |
          | 7 | 4  | 11 |
          | 1 | -1 | 0  |
        """

      val steps = Vector(
        WithDataInputStep(nested, inputs)
      )
      val s = Scenario("scenario with WithDataInput", steps)
      val res = engine.runScenario(Session.newSession)(s)
      res.isSuccess should be(true)
    }
  }

}
