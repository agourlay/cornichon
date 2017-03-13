package engine

import java.util.concurrent.Executors

import akka.actor.{ ActorSystem, Scheduler }
import cats.instances.int._
import com.github.agourlay.cornichon.core.{ Engine, Scenario, Session }
import com.github.agourlay.cornichon.resolver.Resolver
import com.github.agourlay.cornichon.steps.regular.assertStep.{ AssertStep, GenericEqualityAssertion }
import org.openjdk.jmh.annotations._
import engine.EngineBench._

import scala.concurrent.{ ExecutionContext, _ }
import scala.concurrent.duration._

@State(Scope.Benchmark)
@BenchmarkMode(Array(Mode.Throughput))
@Warmup(iterations = 20)
@Measurement(iterations = 20)
@Fork(value = 1)
class EngineBench {

  var actorSystem: ActorSystem = _
  var scheduler: Scheduler = _
  var ec: ExecutionContextExecutorService = _
  var engine: Engine = _

  @Setup(Level.Trial)
  final def beforeAll: Unit = {
    println("")
    println("Creating ActorSystem...")
    actorSystem = ActorSystem("cornichon-actor-system")
    scheduler = actorSystem.scheduler
    println("Creating Engine...")
    val resolver = Resolver.withoutExtractor()
    ec = ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(2))
    engine = Engine.withStepTitleResolver(resolver, ec)(scheduler)
  }

  @TearDown(Level.Trial)
  final def afterAll: Unit = {
    println("")
    println(s"Shutting down ActorSystem...")
    Await.result(actorSystem.terminate(), Duration.Inf)
    println("Shutting down ExecutionContext...")
    ec.shutdown()
  }

//  [info] Benchmark                 Mode  Cnt    Score   Error  Units
//  [info] EngineBench.lotsOfSteps  thrpt   20  360.970 ± 4.298  ops/s
  @Benchmark
  def lotsOfSteps = {
    val f = engine.runScenario(session)(scenario)
    val res = Await.result(f, Duration.Inf)
    assert(res.isSuccess)
  }
}

object EngineBench {
  val session = Session.newEmpty
  val step = AssertStep("addition step", s ⇒ GenericEqualityAssertion(2 + 1, 3))
  val steps = List.fill(1000)(step)
  val scenario = Scenario("test", steps)
}
