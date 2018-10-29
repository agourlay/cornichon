package engine

import java.util.concurrent.{ ExecutorService, Executors }

import cats.instances.int._
import com.github.agourlay.cornichon.core.{ Engine, Scenario, Session }
import com.github.agourlay.cornichon.resolver.PlaceholderResolver
import com.github.agourlay.cornichon.steps.regular.EffectStep
import com.github.agourlay.cornichon.steps.regular.assertStep.{ AssertStep, Assertion, GenericEqualityAssertion }
import org.openjdk.jmh.annotations._
import engine.RunScenarioBench._
import monix.execution.Scheduler

import scala.concurrent.Await
import scala.concurrent.duration._

@State(Scope.Benchmark)
@BenchmarkMode(Array(Mode.Throughput))
@Warmup(iterations = 20)
@Measurement(iterations = 20)
@Fork(value = 1, jvmArgsAppend = Array(
  "-XX:+UnlockCommercialFeatures",
  "-XX:+FlightRecorder",
  "-XX:StartFlightRecording=duration=60s,filename=./profiling-data.jfr,name=profile,settings=profile",
  "-XX:FlightRecorderOptions=settings=/Library/Java/JavaVirtualMachines/jdk1.8.0_162.jdk/Contents/Home/jre/lib/jfr/profile.jfc,samplethreads=true",
  "-Xmx1G"))
class RunScenarioBench {

  //sbt:benchmarks> jmh:run .*RunScenario.* -prof gc -foe true -gc true -rf csv

  @Param(Array("10", "20", "50", "100", "200"))
  var stepsNumber: String = ""
  var es: ExecutorService = _
  var scheduler: Scheduler = _
  var engine: Engine = _

  @Setup(Level.Trial)
  final def beforeAll(): Unit = {
    println("")
    println("Creating Engine...")
    val resolver = PlaceholderResolver.withoutExtractor()
    es = Executors.newFixedThreadPool(1)
    scheduler = Scheduler(es)
    engine = Engine.withStepTitleResolver(resolver)
  }

  @TearDown(Level.Trial)
  final def afterAll(): Unit = {
    println("")
    println("Shutting down ExecutionContext...")
    es.shutdown()
  }

  //    [info] Benchmark                (stepsNumber)   Mode  Cnt       Score      Error  Units
  //    [info] RunScenarioBench.lotsOfSteps             10  thrpt   20  163803.211 ± 596.201  ops/s
  //    [info] RunScenarioBench.lotsOfSteps             20  thrpt   20  101749.604 ± 181.777  ops/s
  //    [info] RunScenarioBench.lotsOfSteps             50  thrpt   20   47216.722 ±  72.985  ops/s
  //    [info] RunScenarioBench.lotsOfSteps            100  thrpt   20   23834.687 ±  35.344  ops/s
  //    [info] RunScenarioBench.lotsOfSteps            200  thrpt   20   12273.652 ±  12.322  ops/s
  @Benchmark
  def lotsOfSteps() = {
    val half = stepsNumber.toInt / 2
    val assertSteps = List.fill(half)(assertStep)
    val effectSteps = List.fill(half)(effectStep)
    val scenario = Scenario("test scenario", setupSession +: (assertSteps ++ effectSteps))
    val f = engine.runScenario(Session.newEmpty)(scenario)
    val res = Await.result(f.runToFuture(scheduler), Duration.Inf)
    assert(res.isSuccess)
  }
}

object RunScenarioBench {
  val setupSession = EffectStep.fromSyncE("setup session", _.addValues("v1" -> "2", "v2" -> "1"))
  val assertStep = AssertStep(
    "addition step",
    s ⇒ Assertion.either {
      for {
        two ← s.get("v1").map(_.toInt)
        one ← s.get("v2").map(_.toInt)
      } yield GenericEqualityAssertion(two + one, 3)
    })
  val effectStep = EffectStep.fromSync("identity", s ⇒ s)
}
