package parsers
import com.github.agourlay.cornichon.json.CornichonJson
import org.openjdk.jmh.annotations.{ Benchmark, BenchmarkMode, Fork, Measurement, Mode, Scope, State, Warmup }

@State(Scope.Benchmark)
@BenchmarkMode(Array(Mode.Throughput))
@Warmup(iterations = 10)
@Measurement(iterations = 10)
@Fork(value = 1, jvmArgsAppend = Array(
  "-XX:+FlightRecorder",
  "-XX:StartFlightRecording=filename=./CornichonJSonBench-profiling-data.jfr,name=profile,settings=profile",
  "-Xmx1G"))
class CornichonJsonBench {

  /*
  [info] Benchmark                                     Mode  Cnt          Score        Error  Units
  [info] CornichonJsonBench.parseDslStringJsonArray   thrpt   10    5100163,535 ±  12479,050  ops/s
  [info] CornichonJsonBench.parseDslStringJsonString  thrpt   10  377827810,866 ± 684456,674  ops/s
  [info] CornichonJsonBench.parseDslStringJsonTable   thrpt   10   46237950,669 ± 191532,790  ops/s
   */

  @Benchmark
  def parseDslStringJsonString() = {
    val res = CornichonJson.parseDslJson("cornichon")
    assert(res.isRight)
  }

  @Benchmark
  def parseDslStringJsonArray() = {
    val res = CornichonJson.parseDslJson("""[ "cornichon", "cornichon" ] """)
    assert(res.isRight)
  }

  @Benchmark
  def parseDslStringJsonTable() = {
    val res = CornichonJson.parseDslJson("""
      Name   |   Age  |
      "John" |   30   |
      "Bob"  |   41   |
    """)
    assert(res.isRight)
  }

}
