package com.github.agourlay.cornichon.steps

import com.github.agourlay.cornichon.core.Engine
import com.github.agourlay.cornichon.dsl.ProvidedInstances
import com.github.agourlay.cornichon.resolver.PlaceholderResolver
import com.github.agourlay.cornichon.util.TaskSpec
import monix.execution.Scheduler

trait StepUtilSpec extends ProvidedInstances with TaskSpec {

  implicit val scheduler = Scheduler.Implicits.global
  val resolver = PlaceholderResolver.withoutExtractor()
  val engine = Engine.withStepTitleResolver(resolver)

}
