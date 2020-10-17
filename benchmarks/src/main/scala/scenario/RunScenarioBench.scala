package scenario

import java.util.concurrent.{ ExecutorService, Executors }

import com.github.agourlay.cornichon.core.{ ScenarioRunner, Scenario, Session }
import com.github.agourlay.cornichon.steps.cats.EffectStep
import com.github.agourlay.cornichon.steps.regular.assertStep.{ AssertStep, Assertion, GenericEqualityAssertion }
import org.openjdk.jmh.annotations._
import scenario.RunScenarioBench._
import monix.execution.Scheduler

import scala.concurrent.Await
import scala.concurrent.duration._

@State(Scope.Benchmark)
@BenchmarkMode(Array(Mode.Throughput))
@Warmup(iterations = 10)
@Measurement(iterations = 10)
@Fork(value = 1, jvmArgsAppend = Array(
  "-XX:+FlightRecorder",
  "-XX:StartFlightRecording=filename=./RunScenarioBench-profiling-data.jfr,name=profile,settings=profile",
  "-Xmx1G"))
class RunScenarioBench {

  //sbt:benchmarks> jmh:run .*RunScenario.* -prof gc -foe true -gc true -rf csv

  @Param(Array("10", "20", "50", "100", "200"))
  var stepsNumber: String = ""
  var es: ExecutorService = _
  var scheduler: Scheduler = _

  @Setup(Level.Trial)
  final def beforeAll(): Unit = {
    es = Executors.newFixedThreadPool(1)
    scheduler = Scheduler(es)
  }

  @TearDown(Level.Trial)
  final def afterAll(): Unit = {
    es.shutdown()
  }

  /*
[info] Benchmark                     (stepsNumber)   Mode  Cnt       Score     Error  Units
[info] RunScenarioBench.lotsOfSteps             10  thrpt   10   83380,462 ±  1033,896  ops/s
[info] RunScenarioBench.lotsOfSteps             20  thrpt   10   48664,650 ±   247,033  ops/s
[info] RunScenarioBench.lotsOfSteps             50  thrpt   10   22002,848 ±    69,904  ops/s
[info] RunScenarioBench.lotsOfSteps            100  thrpt   10   11607,660 ±    39,119  ops/s
[info] RunScenarioBench.lotsOfSteps            200  thrpt   10    5021,917 ±    28,668  ops/s
 */

  @Benchmark
  def lotsOfSteps() = {
    val half = stepsNumber.toInt / 2
    val assertSteps = List.fill(half)(assertStep)
    val effectSteps = List.fill(half)(effectStep)
    val scenario = Scenario("test scenario", setupSession +: (assertSteps ++ effectSteps))
    val f = ScenarioRunner.runScenario(Session.newEmpty)(scenario)
    val res = Await.result(f.runToFuture(scheduler), Duration.Inf)
    assert(res.isSuccess)
  }
}

object RunScenarioBench {
  val setupSession = EffectStep.fromSyncE("setup session", _.session.addValues("v1" -> "2", "v2" -> "1"))
  val assertStep = AssertStep(
    "addition step",
    sc => Assertion.either {
      for {
        two <- sc.session.get("v1").map(_.toInt)
        one <- sc.session.get("v2").map(_.toInt)
      } yield GenericEqualityAssertion(two + one, 3)
    })
  val effectStep = EffectStep.fromSync("identity", _.session)
}
