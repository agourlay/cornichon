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

  @Param(Array("10", "50", "100"))
  var transitionNumber: String = ""

  /*
  [info] Benchmark                (transitionNumber)   Mode  Cnt      Score      Error  Units
  [info] CheckStepBench.runModel                  10  thrpt   10  33383.579 ±  514.614  ops/s
  [info] CheckStepBench.runModel                  50  thrpt   10  11429.354 ±  239.625  ops/s
  [info] CheckStepBench.runModel                 100  thrpt   10   5340.748 ± 1002.334  ops/s
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
  private def integerGen(rc: RandomContext): ValueGenerator[Int] = ValueGenerator(
    name = "integer",
    gen = () => rc.nextInt(10000))

  private def dummyProperty1(name: String): PropertyN[Int, NoValue, NoValue, NoValue, NoValue, NoValue] =
    Property1(
      description = name,
      invariant = g => EffectStep.fromSyncE("add generated", _.session.addValue("generated", g().toString)))

  private val starting = dummyProperty1("starting action")
  private val otherAction = dummyProperty1("other action")
  private val otherActionTwo = dummyProperty1("other action two")
  private val transitions = Map(
    starting -> ((100, otherAction) :: Nil),
    otherAction -> ((100, otherActionTwo) :: Nil),
    otherActionTwo -> ((100, otherAction) :: Nil))
  private val model = Model("model with empty transition for starting", starting, transitions)
  private val modelRunner = ModelRunner.make(integerGen)(model)

}

