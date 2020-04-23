package session

import com.github.agourlay.cornichon.core.Session
import org.openjdk.jmh.annotations._
import session.AddValuesBench._

@State(Scope.Benchmark)
@BenchmarkMode(Array(Mode.Throughput))
@Warmup(iterations = 10)
@Measurement(iterations = 10)
@Fork(value = 1, jvmArgsAppend = Array(
  "-XX:+FlightRecorder",
  "-XX:StartFlightRecording=filename=./AddValuesBench-profiling-data.jfr,name=profile,settings=profile",
  "-Xmx1G"))
class AddValuesBench {

  //sbt:benchmarks> jmh:run .*AddValues.* -prof gc -foe true -gc true -rf csv

  @Param(Array("1", "2", "3", "4", "5", "10"))
  var insertNumber: String = ""

  /*
  [info] Benchmark                 (insertNumber)   Mode  Cnt        Score       Error  Units
  [info] AddValuesBench.addValues               1  thrpt   10  6139574,714 ± 17220,088  ops/s
  [info] AddValuesBench.addValues               2  thrpt   10  2720783,933 ±  7727,835  ops/s
  [info] AddValuesBench.addValues               3  thrpt   10  1699018,869 ±  1625,251  ops/s
  [info] AddValuesBench.addValues               4  thrpt   10  1301837,618 ±  3594,495  ops/s
  [info] AddValuesBench.addValues               5  thrpt   10  1098475,777 ±  3929,211  ops/s
  [info] AddValuesBench.addValues              10  thrpt   10   548834,559 ±  2547,712  ops/s
  */
  @Benchmark
  def addValues() = {
    val values = List.fill(insertNumber.toInt)(tupleEntry)
    Session.newEmpty.addValues(values: _*)
  }
}

object AddValuesBench {
  val tupleEntry = "key" -> "value"
}

