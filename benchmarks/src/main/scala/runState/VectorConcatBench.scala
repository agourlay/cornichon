package runState

import org.openjdk.jmh.annotations._

import scala.collection.immutable.VectorBuilder

@State(Scope.Benchmark)
@BenchmarkMode(Array(Mode.Throughput))
@Warmup(iterations = 10)
@Measurement(iterations = 10)
@Fork(value = 1, jvmArgsAppend = Array(
  "-XX:+UnlockCommercialFeatures",
  "-XX:+FlightRecorder",
  "-XX:StartFlightRecording=duration=60s,filename=./profiling-data.jfr,name=profile,settings=profile",
  "-XX:FlightRecorderOptions=settings=/Library/Java/JavaVirtualMachines/jdk1.8.0_192.jdk/Contents/Home/jre/lib/jfr/profile.jfc,samplethreads=true",
  "-Xmx1G"))
class VectorConcatBench {

  @Param(Array("10", "100", "200"))
  var vectorSize: String = _

  //    [info] Benchmark                                 (vectorSize)   Mode  Cnt        Score        Error  Units
  //    [info] runState.VectorBench.appendBuilderConcat            10  thrpt   20  2432530.959 ±  89466.081  ops/s
  //    [info] runState.VectorBench.appendBuilderConcat           100  thrpt   20   424149.794 ±  19299.410  ops/s
  //    [info] runState.VectorBench.appendBuilderConcat           200  thrpt   20   194206.125 ±   4527.268  ops/s
  //    [info] runState.VectorBench.appendConcat                   10  thrpt   20  2129638.999 ±  30065.564  ops/s
  //    [info] runState.VectorBench.appendConcat                  100  thrpt   20   226621.564 ±   2992.706  ops/s
  //    [info] runState.VectorBench.appendConcat                  200  thrpt   20   119348.390 ±   1195.975  ops/s
  //    [info] runState.VectorBench.concat                         10  thrpt   20  3223074.085 ±  61863.741  ops/s
  //    [info] runState.VectorBench.concat                        100  thrpt   20   588841.963 ±   5475.824  ops/s
  //    [info] runState.VectorBench.concat                        200  thrpt   20   312584.002 ±   3946.295  ops/s

  @Benchmark
  def concat() = {
    val right = Vector.fill(vectorSize.toInt)(vectorSize)
    val left = Vector.fill(vectorSize.toInt)(vectorSize)
    val res = right ++ left
    assert(res.size == vectorSize.toInt * 2)
  }

  @Benchmark
  def appendConcat() = {
    val right = Vector.fill(vectorSize.toInt)(vectorSize)
    val left = Vector.fill(vectorSize.toInt)(vectorSize)
    val res = left.foldLeft(right)((v, o) ⇒ v :+ o)
    assert(res.size == vectorSize.toInt * 2)
  }

  @Benchmark
  def appendBuilderConcat() = {
    val right = Vector.fill(vectorSize.toInt)(vectorSize)
    val left = Vector.fill(vectorSize.toInt)(vectorSize)
    val builder: VectorBuilder[String] = new VectorBuilder[String]
    builder ++= left
    builder ++= right
    val res = builder.result()
    assert(res.size == vectorSize.toInt * 2)
  }

}
