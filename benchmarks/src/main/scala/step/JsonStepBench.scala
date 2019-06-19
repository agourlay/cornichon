package step

import java.util.concurrent.{ ExecutorService, Executors }

import com.github.agourlay.cornichon.core.{ ScenarioRunner, Scenario, Session, SessionKey }
import com.github.agourlay.cornichon.json.JsonSteps.JsonStepBuilder
import com.github.agourlay.cornichon.matchers.MatcherResolver
import com.github.agourlay.cornichon.resolver.PlaceholderResolver
import monix.execution.Scheduler
import org.openjdk.jmh.annotations._
import cats.instances.string._

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import step.JsonStepBench._

@State(Scope.Benchmark)
@BenchmarkMode(Array(Mode.Throughput))
@Warmup(iterations = 10)
@Measurement(iterations = 10)
@Fork(value = 1, jvmArgsAppend = Array(
  "-XX:+UnlockCommercialFeatures",
  "-XX:+FlightRecorder",
  "-XX:StartFlightRecording=duration=60s,filename=./JsonStepBench-profiling-data.jfr,name=profile,settings=profile",
  "-XX:FlightRecorderOptions=settings=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home/jre/lib/jfr/profile.jfc,samplethreads=true",
  "-Xmx1G"))
class JsonStepBench {

  //sbt:benchmarks> jmh:run .*JsonStep.* -prof gc -foe true -gc true -rf csv

  var es: ExecutorService = _
  var scheduler: Scheduler = _

  @Setup(Level.Trial)
  final def beforeAll(): Unit = {
    println("")
    println("Creating Engine...")

    es = Executors.newFixedThreadPool(1)
    scheduler = Scheduler(es)
  }

  @TearDown(Level.Trial)
  final def afterAll(): Unit = {
    println("")
    println("Shutting down ExecutionContext...")
    es.shutdown()
  }

  /*
[info] Benchmark                          Mode  Cnt       Score      Error  Units
[info] JsonStepBench.jsonIgnoreIs        thrpt   10   173287.000 ±   851.190  ops/s
[info] JsonStepBench.jsonIs              thrpt   10   160768.072 ±   283.836  ops/s
[info] JsonStepBench.jsonMatchersIs      thrpt   10    48469.556 ±    80.382  ops/s
[info] JsonStepBench.jsonPathIs          thrpt   10   329925.168 ±  1879.597  ops/s
[info] JsonStepBench.jsonPlaceholdersIs  thrpt   10    50429.236 ±   187.052  ops/s
[info] JsonStepBench.jsonWhitelistingIs  thrpt   10   129291.143 ±   893.171  ops/s
  */

  @Benchmark
  def jsonIs() = {
    val step = jsonStepBuilder.is(json)
    val s = Scenario("scenario with JsonSteps", step :: Nil)
    val f = ScenarioRunner.runScenario(session)(s)
    val res = Await.result(f.runToFuture(scheduler), Duration.Inf)
    assert(res.isSuccess)
  }

  @Benchmark
  def jsonPlaceholdersIs() = {
    val s2 = session.addValuesUnsafe(
      "name" -> "Batman",
      "realName" -> "Bruce Wayne",
      "city" -> "Gotham city",
      "hasSuperpowers" -> "false",
      "publisher-name" -> "DC",
      "publisher-foundation-year" -> "1934",
      "publisher-location" -> "Burbank, California")
    val step = jsonStepBuilder.is(
      """
      {
        "name": "<name>",
        "realName": "<realName>",
        "city": "<city>",
        "hasSuperpowers": <hasSuperpowers>,
        "publisher":{
          "name": "<publisher-name>",
          "foundationYear": <publisher-foundation-year>,
          "location": "<publisher-location>"
        }
      }
      """)
    val s = Scenario("scenario with JsonSteps", step :: Nil)
    val f = ScenarioRunner.runScenario(s2)(s)
    val res = Await.result(f.runToFuture(scheduler), Duration.Inf)
    assert(res.isSuccess)
  }

  @Benchmark
  def jsonMatchersIs() = {
    val step = jsonStepBuilder.is(
      """
      {
        "name": *any-string*,
        "realName": *any-string*,
        "city": *any-string*,
        "hasSuperpowers": *any-boolean*,
        "publisher":{
          "name": *any-string*,
          "foundationYear": *any-integer*,
          "location": *any-string*
        }
      }
      """)
    val s = Scenario("scenario with JsonSteps", step :: Nil)
    val f = ScenarioRunner.runScenario(session)(s)
    val res = Await.result(f.runToFuture(scheduler), Duration.Inf)
    assert(res.isSuccess)
  }

  @Benchmark
  def jsonIgnoreIs() = {
    val step = jsonStepBuilder.ignoring("publisher").is(
      """
      {
        "name": "Batman",
        "realName": "Bruce Wayne",
        "city": "Gotham city",
        "hasSuperpowers": false
      }
    """)
    val s = Scenario("scenario with JsonSteps", step :: Nil)
    val f = ScenarioRunner.runScenario(session)(s)
    val res = Await.result(f.runToFuture(scheduler), Duration.Inf)
    assert(res.isSuccess)
  }

  @Benchmark
  def jsonPathIs() = {
    val step = jsonStepBuilder.path("publisher.name").is("DC")
    val s = Scenario("scenario with JsonSteps", step :: Nil)
    val f = ScenarioRunner.runScenario(session)(s)
    val res = Await.result(f.runToFuture(scheduler), Duration.Inf)
    assert(res.isSuccess)
  }

  @Benchmark
  def jsonWhitelistingIs() = {
    val step = jsonStepBuilder.whitelisting.is("""{"hasSuperpowers": false}""")
    val s = Scenario("scenario with JsonSteps", step :: Nil)
    val f = ScenarioRunner.runScenario(session)(s)
    val res = Await.result(f.runToFuture(scheduler), Duration.Inf)
    assert(res.isSuccess)
  }
}

object JsonStepBench {
  val resolver = PlaceholderResolver.default()
  val matcherResolver = MatcherResolver()
  val testKey = "test-key"
  val jsonStepBuilder = JsonStepBuilder(resolver, matcherResolver, SessionKey(testKey), Some("test body"))
  val json = """
    {
      "name": "Batman",
      "realName": "Bruce Wayne",
      "city": "Gotham city",
      "hasSuperpowers": false,
      "publisher":{
        "name":"DC",
        "foundationYear":1934,
        "location":"Burbank, California"
      }
    }
    """
  val session = Session.newEmpty.addValuesUnsafe(testKey -> json)
}

