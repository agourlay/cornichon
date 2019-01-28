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
  "-XX:FlightRecorderOptions=settings=/Library/Java/JavaVirtualMachines/jdk1.8.0_192.jdk/Contents/Home/jre/lib/jfr/profile.jfc,samplethreads=true",
  "-Xmx1G"))
class AddValuesBench {

  //sbt:benchmarks> jmh:run .*AddValues.* -prof gc -foe true -gc true -rf csv

  @Param(Array("1", "2", "3", "5", "10"))
  var insertNumber: String = ""

  /*
  [info] Benchmark                 (insertNumber)   Mode  Cnt        Score       Error  Units
  [info] AddValuesBench.addValues               1   thrpt   10  6748475.809 ± 12173.150  ops/s
  [info] AddValuesBench.addValues               2   thrpt   10  4051639.418 ± 29472.053  ops/s
  [info] AddValuesBench.addValues               3   thrpt   10  2852314.554 ± 51149.080  ops/s
  [info] AddValuesBench.addValues               5   thrpt   10  1828054.454 ± 21332.395  ops/s
  [info] AddValuesBench.addValues              10   thrpt   10   988718.896 ±  4052.185  ops/s
  */
  @Benchmark
  def addValues() = {
    val s = Session.newEmpty
    val values = List.fill(insertNumber.toInt)("key" -> "value")
    s.addValues(values: _*)
  }
}

