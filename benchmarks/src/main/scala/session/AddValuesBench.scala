package session

import com.github.agourlay.cornichon.core.Session
import org.openjdk.jmh.annotations._
import session.AddValuesBench._

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
  [info] AddValuesBench.addValues               1  thrpt   10  6340605.917 ± 1144511.766  ops/s
  [info] AddValuesBench.addValues               2  thrpt   10  4407327.951 ±   21119.301  ops/s
  [info] AddValuesBench.addValues               3  thrpt   10  2164558.453 ±    3107.111  ops/s
  [info] AddValuesBench.addValues               5  thrpt   10  1488271.191 ±    3462.505  ops/s
  [info] AddValuesBench.addValues              10  thrpt   10   809096.566 ±    3380.935  ops/s
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

