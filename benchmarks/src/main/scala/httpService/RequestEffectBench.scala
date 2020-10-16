package httpService

import java.util.concurrent.{ ExecutorService, Executors }

import com.github.agourlay.cornichon.core.{ Config, ScenarioContext }
import com.github.agourlay.cornichon.http.{ HttpMethods, HttpRequest, HttpService }
import org.openjdk.jmh.annotations._
import RequestEffectBench._
import com.github.agourlay.cornichon.http.client.NoOpHttpClient
import monix.execution.Scheduler

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

  var es: ExecutorService = _
  val client = new NoOpHttpClient
  var httpService: HttpService = _

  @Setup(Level.Trial)
  final def beforeAll(): Unit = {
    es = Executors.newFixedThreadPool(1)
    val scheduler = Scheduler(es)
    httpService = new HttpService("", 2000.millis, client, Config())(scheduler)
  }

  @TearDown(Level.Trial)
  final def afterAll(): Unit = {
    es.shutdown()
  }
  /*
[info] Benchmark                          Mode  Cnt        Score      Error  Units
[info] RequestEffectBench.singleRequest  thrpt   10   256459,872 ±  2002,169  ops/s
*/

  @Benchmark
  def singleRequest() = {
    val f = httpService.requestEffect(request)
    val res = Await.result(f(scenarioContext), Duration.Inf)
    assert(res.isRight)
  }
}

object RequestEffectBench {
  val scenarioContext = ScenarioContext.empty
  val request = HttpRequest[String](
    method = HttpMethods.GET,
    url = "https://myUrl/my/segment",
    body = Some(""" { "k1":"v1", "k2":"v2","k3":"v3","k4":"v4" } """),
    params = ("q1", "v1") :: ("q2", "v2") :: ("q3", "v3") :: Nil,
    headers = ("h1", "v1") :: ("h2", "v2") :: ("h3", "v3") :: Nil)
}