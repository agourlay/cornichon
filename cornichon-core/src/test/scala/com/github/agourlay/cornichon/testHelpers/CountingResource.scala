package com.github.agourlay.cornichon.testHelpers

import java.util.concurrent.atomic.AtomicInteger
import com.github.agourlay.cornichon.core.{BasicError, Step}
import com.github.agourlay.cornichon.steps.cats.EffectStep
import com.github.agourlay.cornichon.steps.wrapped.ScenarioResourceStep

// A scenario resource counting its acquisitions and releases: each acquisition must be released exactly once
class CountingResource {
  val acquired = new AtomicInteger(0)
  val released = new AtomicInteger(0)

  val step: Step = ScenarioResourceStep(
    "counting resource",
    EffectStep.fromSync("acquire", sc => { acquired.incrementAndGet(); sc.session }),
    EffectStep.fromSync("release", sc => { released.incrementAndGet(); sc.session })
  )

}

object CountingResource {

  // fails its first `n` runs, then succeeds
  def failingFirst(n: Int): Step = {
    val runs = new AtomicInteger(0)
    EffectStep.fromSyncE("fails the first runs", sc => if (runs.incrementAndGet() <= n) Left(BasicError("not yet")) else Right(sc.session))
  }

}
