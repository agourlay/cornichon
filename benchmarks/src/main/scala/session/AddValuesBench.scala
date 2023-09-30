package session

import com.github.agourlay.cornichon.core.Session
import org.openjdk.jmh.annotations._
import session.AddValuesBench._

@State(Scope.Benchmark)
@BenchmarkMode(Array(Mode.Throughput))
@Warmup(iterations = 5)
@Measurement(iterations = 10)
@Fork(value = 1, jvmArgsAppend = Array(
  "-XX:+FlightRecorder",
  "-XX:StartFlightRecording=filename=./AddValuesBench-profiling-data.jfr,name=profile,settings=profile",
  "-Xmx1G"))
class AddValuesBench {

  //sbt:benchmarks> jmh:run .*AddValues.* -prof gc -foe true -gc true -rf csv

  @Param(Array("1", "2", "4", "8"))
  var insertNumber: String = ""

  /*
  [info] Benchmark                 (insertNumber)   Mode  Cnt         Score       Error  Units
  [info] AddValuesBench.addValues               1  thrpt   10  10595265.115 ± 32853.337  ops/s
  [info] AddValuesBench.addValues               2  thrpt   10   5642418.788 ± 24496.068  ops/s
  [info] AddValuesBench.addValues               4  thrpt   10   3074429.617 ±  4732.138  ops/s
  [info] AddValuesBench.addValues               8  thrpt   10   2493099.455 ± 62374.398  ops/s
  */
  @Benchmark
  def addValues() = {
    val values = insertNumber match {
      case "1" => oneEntry
      case "2" => twoEntries
      case "4" => fourEntries
      case "8" => eightEntries
    }
    val s2 = Session.newEmpty.addValues(values: _*)
    assert(s2.isRight)
  }
}

object AddValuesBench {
  private val tupleEntry = "key" -> "value"

  private val oneEntry = tupleEntry :: Nil
  private val twoEntries = List.fill(2)(tupleEntry)
  private val fourEntries = List.fill(4)(tupleEntry)
  private val eightEntries = List.fill(5)(tupleEntry)
}

