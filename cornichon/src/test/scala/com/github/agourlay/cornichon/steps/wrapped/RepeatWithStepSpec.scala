package com.github.agourlay.cornichon.steps.wrapped

import cats.instances.boolean._
import cats.instances.string._
import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.steps.StepUtilSpec
import com.github.agourlay.cornichon.steps.regular.assertStep.{ AssertStep, GenericEqualityAssertion }
import org.scalatest.{ Matchers, AsyncWordSpec }

class RepeatWithStepSpec extends AsyncWordSpec with Matchers with StepUtilSpec {

  "RepeatWithStep" must {
    "fail if 'repeat' block contains a failed step" in {
      val nested = AssertStep(
        "always fails",
        s ⇒ GenericEqualityAssertion(true, false)
      ) :: Nil
      val repeatStep = RepeatWithStep(nested, Seq("1", "2", "3"), "indice")
      val s = Scenario("scenario with Repeat", repeatStep :: Nil)
      engine.runScenario(Session.newEmpty)(s).map(_.isSuccess should be(false))
    }

    "repeat steps inside a 'repeat' block" in {
      var uglyCounter = 0
      val loop = 5
      val nested = AssertStep(
        "increment captured counter",
        s ⇒ {
          uglyCounter = uglyCounter + 1
          GenericEqualityAssertion(true, true)
        }
      ) :: Nil
      val repeatStep = RepeatWithStep(nested, Seq("1", "2", "3", "4", "5"), "indice")
      val s = Scenario("scenario with Repeat", repeatStep :: Nil)
      engine.runScenario(Session.newEmpty)(s).map { res ⇒
        res.isSuccess should be(true)
        uglyCounter should be(loop)
      }
    }

    "expose indice in session" in {
      var uglyCounter = 0
      val loop = 5
      val indiceKeyName = "my-counter"
      val nested = AssertStep(
        "increment captured counter",
        s ⇒ {
          uglyCounter = uglyCounter + 1
          GenericEqualityAssertion(s.get(indiceKeyName), uglyCounter.toString)
        }
      ) :: Nil
      val repeatStep = RepeatWithStep(nested, Seq("1", "2", "3", "4", "5"), indiceKeyName)
      val s = Scenario("scenario with Repeat", repeatStep :: Nil)
      engine.runScenario(Session.newEmpty)(s).map { res ⇒
        res.isSuccess should be(true)
        uglyCounter should be(loop)
      }
    }
  }
}
