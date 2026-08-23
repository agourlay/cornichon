package com.github.agourlay.cornichon.steps.wrapped

import cats.effect.IO
import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.dsl.BlockScopedResource
import com.github.agourlay.cornichon.steps.cats.EffectStep
import com.github.agourlay.cornichon.testHelpers.CommonTestSuite
import munit.FunSuite

import java.util.concurrent.atomic.AtomicInteger

class WithBlockScopedResourceSpec extends FunSuite with CommonTestSuite {

  // minimal resource, the interesting part is how the block merges the nested RunState back
  private val transparentResource = new BlockScopedResource {
    val sessionTarget = "test-resource"
    val openingTitle = "opening test resource"
    val closingTitle = "closing test resource"

    def use[A](outsideRunState: RunState)(runInside: RunState => IO[A]): IO[(Session, A)] =
      runInside(outsideRunState).map(a => (Session.newEmpty, a))
  }

  private def countingResourceStep(title: String, releaseCount: AtomicInteger) =
    ScenarioResourceStep(
      title = title,
      acquire = EffectStep.fromSyncE("acquire", _.session.addValue(title, "acquired")),
      release = EffectStep.fromSync("release", sc => { releaseCount.incrementAndGet(); sc.session })
    )

  test("propagates cleanup steps registered inside the block") {
    val released = new AtomicInteger(0)
    val block = WithBlockScopedResource(countingResourceStep("inner", released) :: Nil, transparentResource)
    val s = Scenario("block with a nested cleanup", block :: Nil)
    val res = awaitIO(ScenarioRunner.runScenario(Session.newEmpty)(s))
    assert(res.isSuccess)
    assertEquals(released.get(), 1, "cleanup step registered inside the block did not run exactly once")
  }

  test("does not re-register cleanup steps already pending outside the block") {
    val outerReleased = new AtomicInteger(0)
    val outer = countingResourceStep("outer", outerReleased)
    val block = WithBlockScopedResource(identityEffectStep :: Nil, transparentResource)
    val s = Scenario("block after an outer cleanup", outer :: block :: Nil)
    val res = awaitIO(ScenarioRunner.runScenario(Session.newEmpty)(s))
    assert(res.isSuccess)
    assertEquals(outerReleased.get(), 1, "cleanup step registered before the block ran more than once")
  }

  test("cleanup steps inside and outside the block each run once") {
    val innerReleased = new AtomicInteger(0)
    val outerReleased = new AtomicInteger(0)
    val outer = countingResourceStep("outer", outerReleased)
    val block = WithBlockScopedResource(countingResourceStep("inner", innerReleased) :: Nil, transparentResource)
    val s = Scenario("nested and outer cleanups", outer :: block :: Nil)
    val res = awaitIO(ScenarioRunner.runScenario(Session.newEmpty)(s))
    assert(res.isSuccess)
    assertEquals(innerReleased.get(), 1, "inner cleanup step did not run exactly once")
    assertEquals(outerReleased.get(), 1, "outer cleanup step did not run exactly once")
  }

  test("propagates cleanup steps even when the block fails") {
    val released = new AtomicInteger(0)
    val nested = countingResourceStep("inner", released) :: brokenEffectStep :: Nil
    val block = WithBlockScopedResource(nested, transparentResource)
    val s = Scenario("failing block with a nested cleanup", block :: Nil)
    val res = awaitIO(ScenarioRunner.runScenario(Session.newEmpty)(s))
    assert(!res.isSuccess)
    assertEquals(released.get(), 1, "cleanup step registered inside a failing block did not run exactly once")
  }

}
