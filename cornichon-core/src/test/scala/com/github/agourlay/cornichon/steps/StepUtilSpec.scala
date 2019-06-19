package com.github.agourlay.cornichon.steps

import com.github.agourlay.cornichon.core.ScenarioRunner
import com.github.agourlay.cornichon.dsl.ProvidedInstances
import com.github.agourlay.cornichon.resolver.PlaceholderResolver
import com.github.agourlay.cornichon.util.TaskSpec
import monix.execution.Scheduler

trait StepUtilSpec extends ProvidedInstances with TaskSpec {

  implicit val scheduler = Scheduler.Implicits.global
  val resolver = PlaceholderResolver.default()
  val engine = new ScenarioRunner(resolver)

}
