package com.github.agourlay.cornichon.steps.regular

import com.github.agourlay.cornichon.core.{ Scenario, ScenarioRunner, Session }
import com.github.agourlay.cornichon.steps.StepUtilSpec
import com.github.agourlay.cornichon.steps.cats.{ EffectStep ⇒ CEffectStep }
import monix.eval.Task
import org.scalatest.{ AsyncWordSpec, Matchers }

import scala.concurrent.Future

class EffectStepSpec extends AsyncWordSpec with Matchers with StepUtilSpec {

  "EffectStep" when {
    "Async" must {
      "return error if an Effect step throw an exception" in {
        val step = EffectStep(title = "buggy effect", _ ⇒ Future { throw new RuntimeException("boom") })
        val s = Scenario("scenario with broken effect step", step :: Nil)
        ScenarioRunner.runScenario(Session.newEmpty)(s).map(_.isSuccess should be(false))
      }
    }

    "Sync" must {
      "return error if an Effect step throw an exception" in {
        val step = EffectStep.fromSync(title = "buggy effect", _ ⇒ throw new RuntimeException("boom"))
        val s = Scenario("scenario with broken effect step", step :: Nil)
        ScenarioRunner.runScenario(Session.newEmpty)(s).map(_.isSuccess should be(false))
      }
    }
  }

  "CatsEffectStep" when {
    "Async" must {
      "return error if an Effect step throw an exception" in {
        val step = CEffectStep[Task](title = "buggy effect", _ ⇒ Task { throw new RuntimeException("boom") })
        val s = Scenario("scenario with broken effect step", step :: Nil)
        ScenarioRunner.runScenario(Session.newEmpty)(s).map(_.isSuccess should be(false))
      }
    }

    "Sync" must {
      "return error if an Effect step throw an exception" in {
        val step = CEffectStep[Task](title = "buggy effect", _ ⇒ throw new RuntimeException("boom"))
        val s = Scenario("scenario with broken effect step", step :: Nil)
        ScenarioRunner.runScenario(Session.newEmpty)(s).map(_.isSuccess should be(false))
      }
    }
  }
}

