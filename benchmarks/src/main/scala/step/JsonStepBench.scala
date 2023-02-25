package step

import cats.effect.unsafe.implicits.global

import com.github.agourlay.cornichon.core.{ ScenarioRunner, Scenario, Session, SessionKey }
import com.github.agourlay.cornichon.json.JsonSteps.JsonStepBuilder

import org.openjdk.jmh.annotations._

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

  /*
  [info] Benchmark                          Mode  Cnt      Score      Error  Units
  [info] JsonStepBench.jsonIgnoreIs        thrpt   10  61534.328 ± 1076.157  ops/s
  [info] JsonStepBench.jsonIs              thrpt   10  61452.807 ± 1458.903  ops/s
  [info] JsonStepBench.jsonMatchersIs      thrpt   10  37250.987 ± 1195.539  ops/s
  [info] JsonStepBench.jsonPathIs          thrpt   10  80718.339 ± 2156.393  ops/s
  [info] JsonStepBench.jsonPlaceholdersIs  thrpt   10  48710.808 ± 1671.528  ops/s
  [info] JsonStepBench.jsonWhitelistingIs  thrpt   10  52029.586 ± 1425.700  ops/s

  */

  @Benchmark
  def jsonIs() = {
    val step = jsonStepBuilder.is(json)
    val s = Scenario("scenario with JsonSteps", step :: Nil)
    val f = ScenarioRunner.runScenario(session)(s)
    val res = Await.result(f.unsafeToFuture(), Duration.Inf)
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
    val res = Await.result(f.unsafeToFuture(), Duration.Inf)
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
    val res = Await.result(f.unsafeToFuture(), Duration.Inf)
    assert(res.isSuccess)
  }

  @Benchmark
  def jsonIgnoreIs() = {
    val step = jsonStepBuilder.ignoring("publisher", "hasSuperpowers", "realName").is(
      """
      {
        "name": "Batman",
        "city": "Gotham city"
      }
    """)
    val s = Scenario("scenario with JsonSteps", step :: Nil)
    val f = ScenarioRunner.runScenario(session)(s)
    val res = Await.result(f.unsafeToFuture(), Duration.Inf)
    assert(res.isSuccess)
  }

  @Benchmark
  def jsonPathIs() = {
    val step = jsonStepBuilder.path("publisher.name").is("DC")
    val s = Scenario("scenario with JsonSteps", step :: Nil)
    val f = ScenarioRunner.runScenario(session)(s)
    val res = Await.result(f.unsafeToFuture(), Duration.Inf)
    assert(res.isSuccess)
  }

  @Benchmark
  def jsonWhitelistingIs() = {
    val step = jsonStepBuilder.whitelisting.is("""{"hasSuperpowers": false}""")
    val s = Scenario("scenario with JsonSteps", step :: Nil)
    val f = ScenarioRunner.runScenario(session)(s)
    val res = Await.result(f.unsafeToFuture(), Duration.Inf)
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

