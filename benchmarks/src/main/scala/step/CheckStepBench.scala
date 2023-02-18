package step

import cats.effect.unsafe.implicits.global

import org.openjdk.jmh.annotations._
import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.steps.check.checkModel._
import com.github.agourlay.cornichon.steps.cats.EffectStep

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import step.JsonStepBench._

@State(Scope.Benchmark)
@BenchmarkMode(Array(Mode.Throughput))
@Warmup(iterations = 10)
@Measurement(iterations = 10)
@Fork(value = 1, jvmArgsAppend = Array(
  "-XX:+FlightRecorder",
  "-XX:StartFlightRecording=filename=./CheckStepBench-profiling-data.jfr,name=profile,settings=profile",
  "-Xmx1G"))
class CheckStepBench {

  //sbt:benchmarks> jmh:run .*CheckStep.* -prof gc -foe true -gc true -rf csv

  @Param(Array("10", "20", "50", "100", "200"))
  var transitionNumber: String = ""

  /*
[info] Benchmark                (transitionNumber)   Mode  Cnt      Score       Error  Units
[info] CheckStepBench.runModel                  10  thrpt   10  36378.014 ±   799.056  ops/s
[info] CheckStepBench.runModel                  20  thrpt   10  24959.581 ±   385.351  ops/s
[info] CheckStepBench.runModel                  50  thrpt   10  11729.564 ±   243.720  ops/s
[info] CheckStepBench.runModel                 100  thrpt   10   5294.653 ±  1435.461  ops/s
[info] CheckStepBench.runModel                 200  thrpt   10   1979.434 ±   1011.579  ops/s
  */

  @Benchmark
  def runModel() = {
    val checkStep = CheckModelStep(maxNumberOfRuns = 1, maxNumberOfTransitions = transitionNumber.toInt, CheckStepBench.modelRunner)
    val s = Scenario("scenario with checkStep", checkStep :: Nil)
    val f = ScenarioRunner.runScenario(session)(s)
    val res = Await.result(f.unsafeToFuture(), Duration.Inf)
    assert(res.isSuccess)
  }

}

object CheckStepBench {
  def integerGen(rc: RandomContext): ValueGenerator[Int] = ValueGenerator(
    name = "integer",
    gen = () => rc.nextInt(10000))

  def dummyProperty1(name: String): PropertyN[Int, NoValue, NoValue, NoValue, NoValue, NoValue] =
    Property1(
      description = name,
      invariant = g => EffectStep.fromSyncE("add generated", _.session.addValue("generated", g().toString)))

  val starting = dummyProperty1("starting action")
  val otherAction = dummyProperty1("other action")
  val otherActionTwo = dummyProperty1("other action two")
  val transitions = Map(
    starting -> ((100, otherAction) :: Nil),
    otherAction -> ((100, otherActionTwo) :: Nil),
    otherActionTwo -> ((100, otherAction) :: Nil))
  val model = Model("model with empty transition for starting", starting, transitions)
  val modelRunner = ModelRunner.make(integerGen)(model)

}

