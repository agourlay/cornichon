package session

import com.github.agourlay.cornichon.core.Session
import org.openjdk.jmh.annotations._

@State(Scope.Benchmark)
@BenchmarkMode(Array(Mode.Throughput))
@Warmup(iterations = 10)
@Measurement(iterations = 10)
@Fork(value = 1, jvmArgsAppend = Array(
  "-XX:+UnlockCommercialFeatures",
  "-XX:+FlightRecorder",
  "-XX:StartFlightRecording=duration=60s,filename=./AddValuesBench-profiling-data.jfr,name=profile,settings=profile",
  "-XX:FlightRecorderOptions=settings=/Library/Java/JavaVirtualMachines/jdk1.8.0_202.jdk/Contents/Home/jre/lib/jfr/profile.jfc,samplethreads=true",
  "-Xmx1G"))
class AddValuesBench {

  //sbt:benchmarks> jmh:run .*AddValues.* -prof gc -foe true -gc true -rf csv

  @Param(Array("1", "2", "3", "5", "10"))
  var insertNumber: String = ""

  /*
  [info] Benchmark                 (insertNumber)   Mode  Cnt        Score       Error  Units
  [info] AddValuesBench.addValues               1   thrpt   10  5868537.796 ± 99466.506  ops/s
  [info] AddValuesBench.addValues               2   thrpt   10  2343205.029 ± 29841.329  ops/s
  [info] AddValuesBench.addValues               3   thrpt   10  1765478.878 ± 31085.742  ops/s
  [info] AddValuesBench.addValues               5   thrpt   10  1193001.461 ± 29921.254  ops/s
  [info] AddValuesBench.addValues              10   thrpt   10   458965.622 ± 10070.368  ops/s
  */
  @Benchmark
  def addValues() = {
    val s = Session.newEmpty
    val values = List.fill(insertNumber.toInt)("key" -> "value")
    s.addValues(values: _*)
  }
}

