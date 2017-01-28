package com.github.agourlay.cornichon.steps

import akka.actor.ActorSystem
import com.github.agourlay.cornichon.core.Engine
import com.github.agourlay.cornichon.resolver.Resolver
import com.github.agourlay.cornichon.util.Instances

import scala.concurrent.ExecutionContext

trait StepUtilSpec extends Instances {

  implicit val ec = ExecutionContext.global
  implicit val scheduler = ActorSystem("cornichon-actor-system").scheduler
  val resolver = Resolver.withoutExtractor()
  val engine = Engine.withStepTitleResolver(resolver, ec)

}
