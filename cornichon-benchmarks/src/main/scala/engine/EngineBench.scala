package engine

import java.util.concurrent.Executors

import akka.actor.ActorSystem
import cats.instances.int._
import com.github.agourlay.cornichon.core.{ Engine, Scenario, Session }
import com.github.agourlay.cornichon.resolver.Resolver
import com.github.agourlay.cornichon.steps.regular.assertStep.{ AssertStep, GenericEqualityAssertion }
import org.openjdk.jmh.annotations._
import engine.EngineBench._

import scala.concurrent._
import scala.concurrent.duration._

@State(Scope.Benchmark)
@BenchmarkMode(Array(Mode.Throughput))
@Warmup(iterations = 20)
@Measurement(iterations = 20)
@Fork(value = 1)
class EngineBench {

  var actorSystem: ActorSystem = _
  var ec: ExecutionContextExecutorService = _
  var engine: Engine = _
  @Param(Array("10", "100", "1000"))
  var stepsNumber: String = _

  @Setup(Level.Trial)
  final def beforeAll: Unit = {
    println("")
    println("Creating ActorSystem...")
    actorSystem = ActorSystem()
    println("Creating Engine...")
    val resolver = Resolver.withoutExtractor()
    ec = ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(2))
    engine = Engine.withStepTitleResolver(resolver, ec)(actorSystem.scheduler)
  }

  @TearDown(Level.Trial)
  final def afterAll: Unit = {
    println("")
    println(s"Shutting down ActorSystem...")
    Await.result(actorSystem.terminate(), Duration.Inf)
    println("Shutting down ExecutionContext...")
    ec.shutdown()
  }

  // [info] Benchmark                (stepsNumber)   Mode  Cnt      Score     Error  Units
  // [info] EngineBench.lotsOfSteps             10  thrpt   20  26756.661 ± 397.431  ops/s
  // [info] EngineBench.lotsOfSteps            100  thrpt   20   3243.976 ±  96.268  ops/s
  // [info] EngineBench.lotsOfSteps           1000  thrpt   20    370.277 ±   4.988  ops/s
  @Benchmark
  def lotsOfSteps = {
    val steps = List.fill(stepsNumber.toInt)(step)
    val scenario = Scenario("test scenario", steps)
    val f = engine.runScenario(session)(scenario)
    val res = Await.result(f, Duration.Inf)
    assert(res.isSuccess)
  }
}

object EngineBench {
  val session = Session.newEmpty
  val step = AssertStep("addition step", s ⇒ GenericEqualityAssertion(2 + 1, 3))
}
