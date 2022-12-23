package parsers

import com.github.agourlay.cornichon.json.CornichonJson
import org.openjdk.jmh.annotations.{ Benchmark, BenchmarkMode, Fork, Measurement, Mode, Scope, State, Warmup }

@State(Scope.Benchmark)
@BenchmarkMode(Array(Mode.Throughput))
@Warmup(iterations = 10)
@Measurement(iterations = 10)
@Fork(value = 1, jvmArgsAppend = Array(
  "-XX:+FlightRecorder",
  "-XX:StartFlightRecording=filename=./CornichonJsonBench-profiling-data.jfr,name=profile,settings=profile",
  "-Xmx1G"))
class CornichonJsonBench {

  /*
  [info] Benchmark                                     Mode  Cnt          Score         Error  Units
  [info] CornichonJsonBench.parseDslStringJsonArray   thrpt   10    4752909.949 ±   49540.744  ops/s
  [info] CornichonJsonBench.parseDslStringJsonString  thrpt   10  365630713.124 ± 1054564.831  ops/s
  [info] CornichonJsonBench.parseDslStringJsonTable   thrpt   10     208773.459 ±     372.958  ops/s
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

}
