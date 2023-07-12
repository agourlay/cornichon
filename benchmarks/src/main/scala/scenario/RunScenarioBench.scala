package scenario

import cats.effect.unsafe.implicits.global
import com.github.agourlay.cornichon.core.{ Scenario, ScenarioRunner, Session, Step }
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

  @Param(Array("10", "100", "1000"))
  var stepsNumber: String = ""

  /*
  [info] Benchmark                     (stepsNumber)   Mode  Cnt      Score    Error  Units
  [info] RunScenarioBench.lotsOfSteps             10  thrpt   10  10977.000 ± 27.546  ops/s
  [info] RunScenarioBench.lotsOfSteps            100  thrpt   10    889.172 ±  7.771  ops/s
  [info] RunScenarioBench.lotsOfSteps           1000  thrpt   10     94.673 ±  0.535  ops/s

 */

  @Benchmark
  def lotsOfSteps() = {
    val steps = stepsNumber match {
      case "10" => ten
      case "100" => hundred
      case "1000" => thousand
    }
    val scenario = Scenario("test scenario", steps)
    val f = ScenarioRunner.runScenario(Session.newEmpty)(scenario)
    val res = Await.result(f.unsafeToFuture(), Duration.Inf)
    assert(res.isSuccess)
  }
}

object RunScenarioBench {
  private val setupSession = EffectStep.fromSyncE("setup session", _.session.addValues("v1" -> "2", "v2" -> "1"))
  private val assertStep = AssertStep(
    "addition step",
    sc => Assertion.either {
      for {
        two <- sc.session.get("v1")
        one <- sc.session.get("v2")
      } yield GenericEqualityAssertion(two.toInt + one.toInt, 3)
    })
  private val effectStep = EffectStep.fromSyncE("identity", _.session.addValue("v3", "3"))
  private def makeSteps(stepsNumber: Int): List[Step] = {
    val half = stepsNumber / 2
    val assertSteps = List.fill(half)(assertStep)
    val effectSteps = List.fill(half)(effectStep)
    setupSession +: (assertSteps ++ effectSteps)
  }

  // avoid rebuilding test data for each benchmark
  private val ten = makeSteps(10)
  private val hundred = makeSteps(100)
  private val thousand = makeSteps(1000)
}
