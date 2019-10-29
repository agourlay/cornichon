package com.github.agourlay.cornichon.steps.regular

import com.github.agourlay.cornichon.core.{ CornichonError, Scenario, ScenarioRunner, Session }
import com.github.agourlay.cornichon.steps.StepUtilSpec
import com.github.agourlay.cornichon.steps.cats.{ EffectStep => CEffectStep }
import monix.eval.Task
import utest._

import scala.concurrent.Future

object EffectStepSpec extends TestSuite with StepUtilSpec {

  val tests = Tests {
    test("EffectStep Async - return error if an Effect step throw an exception") {
      val step = EffectStep(title = "buggy effect", _ => throw new RuntimeException("boom"))
      val s = Scenario("scenario with broken effect step", step :: Nil)
      val res = awaitTask(ScenarioRunner.runScenario(Session.newEmpty)(s))
      assert(!res.isSuccess)
    }

    test("EffectStep Async - return error if an Effect step for Future.failed") {
      val step = EffectStep(title = "buggy effect", _ => Future.failed(new RuntimeException("boom")))
      val s = Scenario("scenario with broken effect step", step :: Nil)
      val res = awaitTask(ScenarioRunner.runScenario(Session.newEmpty)(s))
      assert(!res.isSuccess)
    }

    test("EffectStep Sync - return error if an Effect step throw an exception") {
      val step = EffectStep.fromSync(title = "buggy effect", _ => throw new RuntimeException("boom"))
      val s = Scenario("scenario with broken effect step", step :: Nil)
      val res = awaitTask(ScenarioRunner.runScenario(Session.newEmpty)(s))
      assert(!res.isSuccess)
    }

    test("EffectStep SyncE - valid if effect is an Either.Right") {
      val step = EffectStep.fromSyncE(title = "valid effect", sc => Right(sc.session))
      val s = Scenario("scenario with valid effect step", step :: Nil)
      val res = awaitTask(ScenarioRunner.runScenario(Session.newEmpty)(s))
      assert(res.isSuccess)
    }

    test("EffectStep SyncE - invalid if effect is an Either.Left") {
      val step = EffectStep.fromSyncE(title = "valid effect", _ => Left(CornichonError.fromString("ohh nooes")))
      val s = Scenario("scenario with invalid effect step", step :: Nil)
      val res = awaitTask(ScenarioRunner.runScenario(Session.newEmpty)(s))
      assert(!res.isSuccess)
    }

    test("CatsEffectStep Async - return error if an Effect step throw an exception") {
      val step = CEffectStep[Task](title = "buggy effect", _ => throw new RuntimeException("boom"))
      val s = Scenario("scenario with broken effect step", step :: Nil)
      val res = awaitTask(ScenarioRunner.runScenario(Session.newEmpty)(s))
      assert(!res.isSuccess)
    }

    test("CatsEffectStep Async - return error if an Effect step for Task.raiseError") {
      val step = CEffectStep[Task](title = "buggy effect", _ => Task.raiseError(new RuntimeException("boom")))
      val s = Scenario("scenario with broken effect step", step :: Nil)
      val res = awaitTask(ScenarioRunner.runScenario(Session.newEmpty)(s))
      assert(!res.isSuccess)
    }

    test("CatsEffectStep Sync - return error if an Effect step throw an exception") {
      val step = CEffectStep[Task](title = "buggy effect", _ => throw new RuntimeException("boom"))
      val s = Scenario("scenario with broken effect step", step :: Nil)
      val res = awaitTask(ScenarioRunner.runScenario(Session.newEmpty)(s))
      assert(!res.isSuccess)
    }

    test("CatsEffectStep SyncE - valid if effect is an Either.Right") {
      val step = CEffectStep.fromSyncE(title = "valid effect", sc => Right(sc.session))
      val s = Scenario("scenario with valid effect step", step :: Nil)
      val res = awaitTask(ScenarioRunner.runScenario(Session.newEmpty)(s))
      assert(res.isSuccess)
    }

    test("CatsEffectStep SyncE - invalid if effect is an Either.Left") {
      val step = CEffectStep.fromSyncE(title = "valid effect", _ => Left(CornichonError.fromString("ohh nooes")))
      val s = Scenario("scenario with invalid effect step", step :: Nil)
      val res = awaitTask(ScenarioRunner.runScenario(Session.newEmpty)(s))
      assert(!res.isSuccess)
    }
  }
}

