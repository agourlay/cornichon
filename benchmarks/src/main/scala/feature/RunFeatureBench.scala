package feature

import cats.effect.unsafe.implicits.global
import com.github.agourlay.cornichon.core.{ Config, FeatureDef, FeatureRunner, Scenario, Step }
import com.github.agourlay.cornichon.dsl.BaseFeature
import com.github.agourlay.cornichon.http.client.NoOpHttpClient
import com.github.agourlay.cornichon.http.{ HttpMethods, HttpRequest, HttpService }
import com.github.agourlay.cornichon.steps.cats.EffectStep
import com.github.agourlay.cornichon.steps.regular.assertStep.{ AssertStep, Assertion, GenericEqualityAssertion }
import org.openjdk.jmh.annotations._
import feature.RunFeatureBench._

import scala.concurrent.Await
import scala.concurrent.duration._

@State(Scope.Benchmark)
@BenchmarkMode(Array(Mode.Throughput))
@Warmup(iterations = 10)
@Measurement(iterations = 10)
@Fork(value = 1, jvmArgsAppend = Array(
  "-XX:+FlightRecorder",
  "-XX:StartFlightRecording=filename=./RunFeatureBench-profiling-data.jfr,name=profile,settings=profile",
  "-Xmx1G"))
class RunFeatureBench {

  // comment beforehand `println(s"Starting scenario '${s.name}'")`
  //sbt:benchmarks> jmh:run .*RunFeature.*

  /*
  [info] Benchmark                 Mode  Cnt    Score   Error  Units
  [info] RunFeatureBench.feature  thrpt   10  216.702 ± 4.261  ops/s
  */

  @Benchmark
  def feature() = {
    val f = featureRunner.runFeature(_ => true)(identity)
    val res = Await.result(f.unsafeToFuture(), Duration.Inf)
    assert(res.nonEmpty)
  }
}

object RunFeatureBench {
  private val setupSession = EffectStep.fromSyncE("setup session", _.session.addValues("v1" -> "value 1", "v2" -> "value 2", "v3" -> "title", "v4" -> "1", "v5" -> "2"))

  val client = new NoOpHttpClient
  val httpService = new HttpService("", 2000.millis, client, Config())(cats.effect.unsafe.implicits.global)

  val request = HttpRequest[String](
    method = HttpMethods.GET,
    url = "https://myUrl/my/segment",
    body = Some(""" { "k1":"<v1>", "k2":"<v2>","k3":"v3","k4":"v4" } """),
    params = ("q1", "<v1>") :: ("q2", "<v2>") :: ("q3", "v3") :: Nil,
    headers = ("h1", "v1") :: ("h2", "v2") :: ("h3", "v3") :: Nil)

  private val assertStep = AssertStep(
    "addition step <v3>",
    sc => Assertion.either {
      for {
        two <- sc.session.get("v4")
        one <- sc.session.get("v5")
      } yield GenericEqualityAssertion(two.toInt + one.toInt, 3)
    })

  private val effectStep = EffectStep("effect step <v3>", httpService.requestEffectIO(request))

  private def makeSteps(stepsNumber: Int): List[Step] = {
    val half = stepsNumber / 2
    val assertSteps = List.fill(half)(assertStep)
    val effectSteps = List.fill(half)(effectStep)
    setupSession +: (assertSteps ++ effectSteps)
  }

  private def makeScenarios(stepsNumber: Int, scenarioNumber: Int): List[Scenario] = {
    Range.inclusive(1, scenarioNumber).map { i =>
      Scenario(s"test scenario $i", makeSteps(stepsNumber))
    }.toList
  }

  private val feature = FeatureDef(
    name = "test feature",
    scenarios = makeScenarios(100, 10))

  private val baseFeature = new BaseFeature {
    override def feature = RunFeatureBench.feature
  }

  private val featureRunner = FeatureRunner(feature, baseFeature, None)
}
