package scenario

import java.util.concurrent.{ ExecutorService, Executors }

import cats.instances.int._
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
  "-XX:+UnlockCommercialFeatures",
  "-XX:+FlightRecorder",
  "-XX:StartFlightRecording=duration=60s,filename=./RunScenarioBench-profiling-data.jfr,name=profile,settings=profile",
  "-XX:FlightRecorderOptions=settings=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home/jre/lib/jfr/profile.jfc,samplethreads=true",
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
[info] RunScenarioBench.lotsOfSteps             10  thrpt   10  181242.825 ±  1134.861  ops/s
[info] RunScenarioBench.lotsOfSteps             20  thrpt   10  111107.596 ±  2055.684  ops/s
[info] RunScenarioBench.lotsOfSteps             50  thrpt   10   51680.691 ±   139.650  ops/s
[info] RunScenarioBench.lotsOfSteps            100  thrpt   10   27624.388 ±    49.305  ops/s
[info] RunScenarioBench.lotsOfSteps            200  thrpt   10   14191.329 ±    72.443  ops/s
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
    sc ⇒ Assertion.either {
      for {
        two ← sc.session.get("v1").map(_.toInt)
        one ← sc.session.get("v2").map(_.toInt)
      } yield GenericEqualityAssertion(two + one, 3)
    })
  val effectStep = EffectStep.fromSync("identity", _.session)
}
