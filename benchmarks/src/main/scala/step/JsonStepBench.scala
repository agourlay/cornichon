package step

import java.util.concurrent.{ ExecutorService, Executors }

import com.github.agourlay.cornichon.core.{ ScenarioRunner, Scenario, Session, SessionKey }
import com.github.agourlay.cornichon.json.JsonSteps.JsonStepBuilder
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
  "-XX:+FlightRecorder",
  "-XX:StartFlightRecording=filename=./JsonStepBench-profiling-data.jfr,name=profile,settings=profile",
  "-Xmx1G"))
class JsonStepBench {

  //sbt:benchmarks> jmh:run .*JsonStep.* -prof gc -foe true -gc true -rf csv

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
[info] Benchmark                          Mode  Cnt       Score      Error  Units
[info] JsonStepBench.jsonIgnoreIs        thrpt   10  120782,209 ± 6525,365  ops/s
[info] JsonStepBench.jsonIs              thrpt   10  114391,169 ± 3921,683  ops/s
[info] JsonStepBench.jsonMatchersIs      thrpt   10   40777,228 ± 1980,125  ops/s
[info] JsonStepBench.jsonPathIs          thrpt   10  180329,882 ± 7782,463  ops/s
[info] JsonStepBench.jsonPlaceholdersIs  thrpt   10   39870,119 ± 1492,777  ops/s
[info] JsonStepBench.jsonWhitelistingIs  thrpt   10   80633,634 ± 4661,314  ops/s
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
  val testKey = "test-key"
  val jsonStepBuilder = JsonStepBuilder(SessionKey(testKey), Some("test body"))
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

