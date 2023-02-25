package httpService

import com.github.agourlay.cornichon.http.client.NoOpHttpClient
import com.github.agourlay.cornichon.core.{ Config, ScenarioContext }
import com.github.agourlay.cornichon.http.{ HttpMethods, HttpRequest, HttpService }
import org.openjdk.jmh.annotations._
import RequestEffectBench._

import scala.concurrent.Await
import scala.concurrent.duration._

@State(Scope.Benchmark)
@BenchmarkMode(Array(Mode.Throughput))
@Warmup(iterations = 10)
@Measurement(iterations = 10)
@Fork(value = 1, jvmArgsAppend = Array(
  "-XX:+FlightRecorder",
  "-XX:StartFlightRecording=filename=./RequestEffectBench-profiling-data.jfr,name=profile,settings=profile",
  "-Xmx1G"))
class RequestEffectBench {

  //sbt:benchmarks> jmh:run .*RequestEffect.*

  /*
[info] Benchmark                          Mode  Cnt        Score      Error  Units
[info] RequestEffectBench.singleRequest  thrpt   10    95729.065 ± 1888.774  ops/s
*/

  @Benchmark
  def singleRequest() = {
    val f = httpService.requestEffect(request)
    val res = Await.result(f(scenarioContext), Duration.Inf)
    assert(res.isRight)
  }
}

object RequestEffectBench {
  val client = new NoOpHttpClient
  val httpService = new HttpService("", 2000.millis, client, Config())(cats.effect.unsafe.implicits.global)

  val scenarioContext = ScenarioContext.empty
  val request = HttpRequest[String](
    method = HttpMethods.GET,
    url = "https://myUrl/my/segment",
    body = Some(""" { "k1":"v1", "k2":"v2","k3":"v3","k4":"v4" } """),
    params = ("q1", "v1") :: ("q2", "v2") :: ("q3", "v3") :: Nil,
    headers = ("h1", "v1") :: ("h2", "v2") :: ("h3", "v3") :: Nil)
}