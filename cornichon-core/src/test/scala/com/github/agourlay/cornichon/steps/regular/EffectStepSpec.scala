package com.github.agourlay.cornichon.steps.regular

import com.github.agourlay.cornichon.core.{ CornichonError, Scenario, ScenarioRunner, Session }
import com.github.agourlay.cornichon.steps.StepUtilSpec
import com.github.agourlay.cornichon.steps.cats.{ EffectStep => CEffectStep }
import monix.eval.Task
import org.scalatest.{ AsyncWordSpec, Matchers }

import scala.concurrent.Future

class EffectStepSpec extends AsyncWordSpec with Matchers with StepUtilSpec {

  "EffectStep" when {
    "Async" must {
      "return error if an Effect step throw an exception" in {
        val step = EffectStep(title = "buggy effect", _ => throw new RuntimeException("boom"))
        val s = Scenario("scenario with broken effect step", step :: Nil)
        ScenarioRunner.runScenario(Session.newEmpty)(s).map(_.isSuccess should be(false))
      }

      "return error if an Effect step for Future.failed" in {
        val step = EffectStep(title = "buggy effect", _ => Future.failed(new RuntimeException("boom")))
        val s = Scenario("scenario with broken effect step", step :: Nil)
        ScenarioRunner.runScenario(Session.newEmpty)(s).map(_.isSuccess should be(false))
      }
    }

    "Sync" must {
      "return error if an Effect step throw an exception" in {
        val step = EffectStep.fromSync(title = "buggy effect", _ => throw new RuntimeException("boom"))
        val s = Scenario("scenario with broken effect step", step :: Nil)
        ScenarioRunner.runScenario(Session.newEmpty)(s).map(_.isSuccess should be(false))
      }
    }

    "SyncE" must {
      "valid if effect is an Either.Right" in {
        val step = EffectStep.fromSyncE(title = "valid effect", sc => Right(sc.session))
        val s = Scenario("scenario with valid effect step", step :: Nil)
        ScenarioRunner.runScenario(Session.newEmpty)(s).map(_.isSuccess should be(true))
      }

      "invalid if effect is an Either.Left" in {
        val step = EffectStep.fromSyncE(title = "valid effect", _ => Left(CornichonError.fromString("ohh nooes")))
        val s = Scenario("scenario with invalid effect step", step :: Nil)
        ScenarioRunner.runScenario(Session.newEmpty)(s).map(_.isSuccess should be(false))
      }
    }
  }

  "CatsEffectStep" when {
    "Async" must {
      "return error if an Effect step throw an exception" in {
        val step = CEffectStep[Task](title = "buggy effect", _ => throw new RuntimeException("boom"))
        val s = Scenario("scenario with broken effect step", step :: Nil)
        ScenarioRunner.runScenario(Session.newEmpty)(s).map(_.isSuccess should be(false))
      }

      "return error if an Effect step for Task.raiseError" in {
        val step = CEffectStep[Task](title = "buggy effect", _ => Task.raiseError(new RuntimeException("boom")))
        val s = Scenario("scenario with broken effect step", step :: Nil)
        ScenarioRunner.runScenario(Session.newEmpty)(s).map(_.isSuccess should be(false))
      }
    }

    "Sync" must {
      "return error if an Effect step throw an exception" in {
        val step = CEffectStep[Task](title = "buggy effect", _ => throw new RuntimeException("boom"))
        val s = Scenario("scenario with broken effect step", step :: Nil)
        ScenarioRunner.runScenario(Session.newEmpty)(s).map(_.isSuccess should be(false))
      }
    }

    "SyncE" must {
      "valid if effect is an Either.Right" in {
        val step = CEffectStep.fromSyncE(title = "valid effect", sc => Right(sc.session))
        val s = Scenario("scenario with valid effect step", step :: Nil)
        ScenarioRunner.runScenario(Session.newEmpty)(s).map(_.isSuccess should be(true))
      }

      "invalid if effect is an Either.Left" in {
        val step = CEffectStep.fromSyncE(title = "valid effect", _ => Left(CornichonError.fromString("ohh nooes")))
        val s = Scenario("scenario with invalid effect step", step :: Nil)
        ScenarioRunner.runScenario(Session.newEmpty)(s).map(_.isSuccess should be(false))
      }
    }
  }
}

