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

  @Param(Array("1", "2", "3", "4", "5", "10"))
  var insertNumber: String = ""

  /*
  [info] Benchmark                 (insertNumber)   Mode  Cnt         Score        Error  Units
  [info] AddValuesBench.addValues               1  thrpt   10  12670412.393 ±  18062.037  ops/s
  [info] AddValuesBench.addValues               2  thrpt   10   6037625.901 ±   9837.719  ops/s
  [info] AddValuesBench.addValues               3  thrpt   10   3507215.404 ±  12345.383  ops/s
  [info] AddValuesBench.addValues               4  thrpt   10   2821795.049 ±   6133.413  ops/s
  [info] AddValuesBench.addValues               5  thrpt   10   2438427.351 ±  10529.552  ops/s
  [info] AddValuesBench.addValues              10  thrpt   10   1169633.058 ±   2101.351  ops/s
  */
  @Benchmark
  def addValues() = {
    val values = insertNumber match {
      case "1" => oneEntry
      case "2" => twoEntries
      case "3" => threeEntries
      case "4" => fourEntries
      case "5" => fiveEntries
      case "10" => tenEntries
    }
    val s2 = Session.newEmpty.addValues(values: _*)
    assert(s2.isRight)
  }
}

object AddValuesBench {
  val tupleEntry = "key" -> "value"

  val oneEntry = tupleEntry :: Nil
  val twoEntries = List.fill(2)(tupleEntry)
  val threeEntries = List.fill(3)(tupleEntry)
  val fourEntries = List.fill(4)(tupleEntry)
  val fiveEntries = List.fill(5)(tupleEntry)
  val tenEntries = List.fill(10)(tupleEntry)
}

