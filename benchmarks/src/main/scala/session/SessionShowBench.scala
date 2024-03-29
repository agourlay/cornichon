package session

import com.github.agourlay.cornichon.core.Session
import org.openjdk.jmh.annotations._
import session.SessionShowBench._
import cats.syntax.show._

@State(Scope.Benchmark)
@BenchmarkMode(Array(Mode.Throughput))
@Warmup(iterations = 5)
@Measurement(iterations = 10)
@Fork(value = 1, jvmArgsAppend = Array(
  "-XX:+FlightRecorder",
  "-XX:StartFlightRecording=filename=./SessionShowBench-profiling-data.jfr,name=profile,settings=profile",
  "-Xmx1G"))
class SessionShowBench {

  //sbt:benchmarks> jmh:run .*SessionShow.* -prof gc -foe true -gc true -rf csv

  /*
  [info] Benchmark                                   Mode  Cnt         Score      Error   Units
  [info] Benchmark                                   Mode  Cnt         Score      Error   Units
  [info] SessionShowBench.show                      thrpt   10        21.646 ±    0.637   ops/s
  [info] SessionShowBench.show:·gc.alloc.rate       thrpt   10       868.195 ±   25.567  MB/sec
  [info] SessionShowBench.show:·gc.alloc.rate.norm  thrpt   10  42059242.169 ± 1005.786    B/op
  [info] SessionShowBench.show:·gc.count            thrpt   10       805.000             counts
  [info] SessionShowBench.show:·gc.time             thrpt   10       706.000                 ms
   */

  @Benchmark
  def show() = {
    val pretty = session.show
    assert(pretty.nonEmpty)
  }
}

object SessionShowBench {
  private val size = 1000
  val session: Session = {
    val map = Map.newBuilder[String, Vector[String]]
    for (i <- 0 until size) {
      map += s"key$i" -> Vector.fill(size)(s"value$i")
    }
    Session(map.result())
  }
}

