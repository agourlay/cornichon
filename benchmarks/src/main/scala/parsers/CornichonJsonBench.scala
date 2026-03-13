package parsers

import com.github.agourlay.cornichon.json.CornichonJson
import io.circe.Json
import org.openjdk.jmh.annotations.{Benchmark, BenchmarkMode, Fork, Measurement, Mode, Scope, State, Warmup}

@State(Scope.Benchmark)
@BenchmarkMode(Array(Mode.Throughput))
@Warmup(iterations = 10)
@Measurement(iterations = 10)
@Fork(
  value = 1,
  jvmArgsAppend = Array("-XX:+FlightRecorder", "-XX:StartFlightRecording=filename=./CornichonJsonBench-profiling-data.jfr,name=profile,settings=profile", "-Xmx1G")
)
class CornichonJsonBench {

  /*
  [info] Benchmark                                          Mode  Cnt          Score         Error  Units
  [info] CornichonJsonBench.parseDslStringJsonArray        thrpt   10    4484269.109 ±   28014.758  ops/s
  [info] CornichonJsonBench.parseDslStringJsonString       thrpt   10  496868205.299 ± 4198099.986  ops/s
  [info] CornichonJsonBench.parseDslStringJsonTable        thrpt   10     248923.409 ±    4802.500  ops/s
   */

  @Benchmark
  def parseDslStringJsonString() = {
    val res = CornichonJson.parseDslJson(" a rather long string about cornichon ")
    assert(res.isRight)
  }

  @Benchmark
  def parseDslStringJsonArray() = {
    val res = CornichonJson.parseDslJson("""[ "a", "very", "cool", "feature" ] """)
    assert(res.isRight)
  }

  @Benchmark
  def parseDslStringJsonTable() = {
    val res = CornichonJson.parseDslJson("""
      | Name   |  Age | City     |
      | "John" |  30  | "Paris"  |
      | "Bob"  |  41  | "Berlin" |
      | "Carl" |  29  | "Milan"  |
    """)
    assert(res.isRight)
  }

  private val nestedJson: Json = CornichonJson
    .parseDslJsonUnsafe("""
    {
      "store": {
        "book": [
          { "category": "reference", "author": "Nigel Rees", "title": "Sayings of the Century", "price": 8.95 },
          { "category": "fiction", "author": "Evelyn Waugh", "title": "Sword of Honour", "price": 12.99 },
          { "category": "fiction", "author": "Herman Melville", "title": "Moby Dick", "isbn": "0-553-21311-3", "price": 8.99 },
          { "category": "fiction", "author": "J. R. R. Tolkien", "title": "The Lord of the Rings", "isbn": "0-395-19395-8", "price": 22.99 }
        ],
        "bicycle": { "color": "red", "price": 19.95 }
      },
      "city": "Paris",
      "country": "France"
    }
  """)

  @Benchmark
  def findAllPathWithValueNested() = {
    val res = CornichonJson.findAllPathWithStringValue(Set("fiction", "Paris"), nestedJson)
    assert(res.length == 4)
  }

}
