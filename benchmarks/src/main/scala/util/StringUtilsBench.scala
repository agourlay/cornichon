package util

import com.github.agourlay.cornichon.util.StringUtils
import org.openjdk.jmh.annotations._

@State(Scope.Benchmark)
@BenchmarkMode(Array(Mode.Throughput))
@Warmup(iterations = 5)
@Measurement(iterations = 10)
@Fork(
  value = 1,
  jvmArgsAppend = Array("-Xmx1G")
)
class StringUtilsBench {

  // sbt:benchmarks> jmh:run .*StringUtils.* -prof gc -foe true -gc true -rf csv

  /*
  [info] Benchmark                                       (pairsNumber)   Mode  Cnt         Score        Error   Units
  [info] StringUtilsBench.printArrowPairs                            3  thrpt   10  15624057.645 ± 164186.114   ops/s
  [info] StringUtilsBench.printArrowPairs:gc.alloc.rate.norm         3  thrpt   10       304.000 ±      0.001    B/op
  [info] StringUtilsBench.printArrowPairs                           10  thrpt   10   6370255.486 ±  58852.096   ops/s
  [info] StringUtilsBench.printArrowPairs:gc.alloc.rate.norm        10  thrpt   10       928.000 ±      0.001    B/op
   */

  @Param(Array("3", "10"))
  var pairsNumber: String = ""

  private var pairs: List[(String, String)] = Nil

  @Setup(Level.Trial)
  def setup(): Unit =
    pairs = List.tabulate(pairsNumber.toInt)(i => (s"name-$i", s"value-$i"))

  @Benchmark
  def printArrowPairs() = {
    val res = StringUtils.printArrowPairs(pairs)
    assert(res.nonEmpty)
  }

}
