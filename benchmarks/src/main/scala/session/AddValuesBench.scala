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
  [info] AddValuesBench.addValues               1  thrpt   10  7221200.170 ± 25630.672  ops/s
  [info] AddValuesBench.addValues               2  thrpt   10  4455484.006 ± 16331.895  ops/s
  [info] AddValuesBench.addValues               3  thrpt   10  2180160.733 ±  3846.665  ops/s
  [info] AddValuesBench.addValues               4  thrpt   10  1751734.128 ±  6915.557  ops/s
  [info] AddValuesBench.addValues               5  thrpt   10  1486845.132 ±  6748.036  ops/s
  [info] AddValuesBench.addValues              10  thrpt   10   784736.989 ± 22766.361  ops/s
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

