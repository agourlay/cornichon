package engine

import java.util.concurrent.{ ExecutorService, Executors }

import cats.instances.int._
import cats.syntax.either._
import com.github.agourlay.cornichon.core.{ Engine, Scenario, Session }
import com.github.agourlay.cornichon.resolver.Resolver
import com.github.agourlay.cornichon.steps.regular.EffectStep
import com.github.agourlay.cornichon.steps.regular.assertStep.{ AssertStep, Assertion, GenericEqualityAssertion }
import org.openjdk.jmh.annotations._
import engine.EngineBench._
import monix.execution.Scheduler

import scala.concurrent.Await
import scala.concurrent.duration._

@State(Scope.Benchmark)
@BenchmarkMode(Array(Mode.Throughput))
@Warmup(iterations = 20)
@Measurement(iterations = 20)
@Fork(value = 1) //@Fork(value = 1, jvmArgsAppend = Array(
//  "-XX:+UnlockCommercialFeatures",
//  "-XX:+FlightRecorder",
//  "-XX:StartFlightRecording=duration=60s,filename=./profiling-data.jfr,name=profile,settings=profile",
//  "-XX:FlightRecorderOptions=settings=./openjdk/jdk1.8.0/jre/lib/jfr/profile.jfc,samplethreads=true"
//))
class EngineBench {

  var es: ExecutorService = _
  var engine: Engine = _
  @Param(Array("10", "100", "1000"))
  var stepsNumber: String = _

  @Setup(Level.Trial)
  final def beforeAll: Unit = {
    println("")
    println("Creating Engine...")
    val resolver = Resolver.withoutExtractor()
    es = Executors.newFixedThreadPool(2)
    val scheduler = Scheduler(es)
    engine = Engine.withStepTitleResolver(resolver)(scheduler)
  }

  @TearDown(Level.Trial)
  final def afterAll: Unit = {
    println("")
    println("Shutting down ExecutionContext...")
    es.shutdown()
  }

  //    [info] Benchmark                (stepsNumber)   Mode  Cnt      Score      Error  Units
  //    [info] EngineBench.lotsOfSteps             10  thrpt   20  16318.944 ± 1349.173  ops/s
  //    [info] EngineBench.lotsOfSteps            100  thrpt   20   2453.874 ±   14.008  ops/s
  //    [info] EngineBench.lotsOfSteps           1000  thrpt   20    247.688 ±   11.477  ops/s
  @Benchmark
  def lotsOfSteps = {
    val assertSteps = List.fill(stepsNumber.toInt / 2)(assertStep)
    val effectSteps = List.fill(stepsNumber.toInt / 2)(effectStep)
    val scenario = Scenario("test scenario", setupSession +: (assertSteps ++ effectSteps))
    val f = engine.runScenario(Session.newEmpty)(scenario)
    val res = Await.result(f, Duration.Inf)
    assert(res.isSuccess)
  }
}

object EngineBench {
  val setupSession = EffectStep.fromSync("setup session", s => s.addValues(Seq("v1" -> "2", "v2" -> "1")))
  val assertStep = AssertStep(
    "addition step",
    s ⇒ Assertion.either {
      for {
        two <- s.get("v1").map(_.toInt)
        one <- s.get("v2").map(_.toInt)
      } yield GenericEqualityAssertion(two + one, 3)
    }
  )
  val effectStep = EffectStep.fromSync("identity", s => s)
}
