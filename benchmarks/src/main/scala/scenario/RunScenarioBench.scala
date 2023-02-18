package scenario

import cats.effect.unsafe.implicits.global
import com.github.agourlay.cornichon.core.{ Scenario, ScenarioRunner, Session }
import com.github.agourlay.cornichon.steps.cats.EffectStep
import com.github.agourlay.cornichon.steps.regular.assertStep.{ AssertStep, Assertion, GenericEqualityAssertion }
import org.openjdk.jmh.annotations._
import scenario.RunScenarioBench._

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

  /*
[info] Benchmark                     (stepsNumber)   Mode  Cnt       Score        Error  Units
[info] RunScenarioBench.lotsOfSteps             10  thrpt   10   52049.545 ±   1112.306  ops/s
[info] RunScenarioBench.lotsOfSteps             20  thrpt   10   42123.140 ±    715.868  ops/s
[info] RunScenarioBench.lotsOfSteps             50  thrpt   10   24596.997 ±    328.325  ops/s
[info] RunScenarioBench.lotsOfSteps            100  thrpt   10   14020.906 ±    241.349  ops/s
[info] RunScenarioBench.lotsOfSteps            200  thrpt   10    7388.638 ±    340.428  ops/s
 */

  @Benchmark
  def lotsOfSteps() = {
    val half = stepsNumber.toInt / 2
    val assertSteps = List.fill(half)(assertStep)
    val effectSteps = List.fill(half)(effectStep)
    val scenario = Scenario("test scenario", setupSession +: (assertSteps ++ effectSteps))
    val f = ScenarioRunner.runScenario(Session.newEmpty)(scenario)
    val res = Await.result(f.unsafeToFuture(), Duration.Inf)
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
