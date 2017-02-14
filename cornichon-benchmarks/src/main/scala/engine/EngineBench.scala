package engine

import akka.actor.ActorSystem
import cats.instances.int._
import com.github.agourlay.cornichon.core.{ Engine, Scenario, Session }
import com.github.agourlay.cornichon.resolver.Resolver
import com.github.agourlay.cornichon.steps.regular.assertStep.{ AssertStep, GenericEqualityAssertion }
import org.openjdk.jmh.annotations.Benchmark
import engine.EngineBench._

import scala.concurrent.ExecutionContext

//TODO improve state management
class EngineBench {

  @Benchmark
  def lotsOfSteps = {
    val s = Scenario("test", tenThousandSteps)
    engine.runScenario(session)(s).map(r => assert(r.isSuccess))(scala.concurrent.ExecutionContext.global)
  }

}

object EngineBench {
  implicit val scheduler = ActorSystem("cornichon-actor-system").scheduler
  val resolver = Resolver.withoutExtractor()
  val engine = Engine.withStepTitleResolver(resolver, ExecutionContext.global)

  val additionStep = AssertStep("addition step", s ⇒ GenericEqualityAssertion(2 + 1, 3))
  val session = Session.newEmpty
  val step = AssertStep("addition step", s ⇒ GenericEqualityAssertion(2 + 1, 3))
  val tenThousandSteps = List.fill(100000)(step)

}
