package session

import com.github.agourlay.cornichon.core.Session
import org.openjdk.jmh.annotations._

@State(Scope.Benchmark)
@BenchmarkMode(Array(Mode.Throughput))
@Warmup(iterations = 20)
@Measurement(iterations = 20)
@Fork(value = 1, jvmArgsAppend = Array(
  "-XX:+UnlockCommercialFeatures",
  "-XX:+FlightRecorder",
  "-XX:StartFlightRecording=duration=60s,filename=./profiling-data.jfr,name=profile,settings=profile",
  "-XX:FlightRecorderOptions=settings=/Library/Java/JavaVirtualMachines/jdk1.8.0_162.jdk/Contents/Home/jre/lib/jfr/profile.jfc,samplethreads=true",
  "-Xmx1G"))
class AddValuesBench {

  //sbt:benchmarks> jmh:run .*AddValues.* -prof gc -foe true -gc true -rf csv

  @Param(Array("1", "2", "3", "5", "10"))
  var insertNumber: String = ""

  /*
  [info] Benchmark                 (insertNumber)   Mode  Cnt        Score       Error  Units
  [info] AddValuesBench.addValues               1  thrpt   20  7576415.769 ± 112344.899  ops/s
  [info] AddValuesBench.addValues               2  thrpt   20  3534526.475 ±  52012.710  ops/s
  [info] AddValuesBench.addValues               3  thrpt   20  2583904.943 ± 152011.680  ops/s
  [info] AddValuesBench.addValues               5  thrpt   20  1757858.319 ±  30525.716  ops/s
  [info] AddValuesBench.addValues              10  thrpt   20   986003.136 ±  14266.643  ops/s
  */
  @Benchmark
  def addValues() = {
    val s = Session.newEmpty
    val values = List.fill(insertNumber.toInt)("key" -> "value")
    s.addValues(values: _*)
  }
}

