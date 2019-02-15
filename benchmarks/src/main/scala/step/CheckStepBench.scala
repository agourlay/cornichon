package step

import java.util.concurrent.{ ExecutorService, Executors }

import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.matchers.MatcherResolver
import com.github.agourlay.cornichon.resolver.PlaceholderResolver
import monix.execution.Scheduler
import org.openjdk.jmh.annotations._
import com.github.agourlay.cornichon.check._
import com.github.agourlay.cornichon.check.checkModel._
import com.github.agourlay.cornichon.steps.cats.EffectStep

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import step.JsonStepBench._

@State(Scope.Benchmark)
@BenchmarkMode(Array(Mode.Throughput))
@Warmup(iterations = 10)
@Measurement(iterations = 10)
@Fork(value = 1, jvmArgsAppend = Array(
  "-XX:+UnlockCommercialFeatures",
  "-XX:+FlightRecorder",
  "-XX:StartFlightRecording=duration=60s,filename=./CheckStepBench-profiling-data.jfr,name=profile,settings=profile",
  "-XX:FlightRecorderOptions=settings=/Library/Java/JavaVirtualMachines/jdk1.8.0_192.jdk/Contents/Home/jre/lib/jfr/profile.jfc,samplethreads=true",
  "-Xmx1G"))
class CheckStepBench {

  //sbt:benchmarks> jmh:run .*CheckStep.* -prof gc -foe true -gc true -rf csv

  @Param(Array("10", "20", "50", "100", "200"))
  var transitionNumber: String = ""

  var es: ExecutorService = _
  var scheduler: Scheduler = _
  var engine: Engine = _

  @Setup(Level.Trial)
  final def beforeAll(): Unit = {
    println("")
    println("Creating Engine...")

    es = Executors.newFixedThreadPool(1)
    scheduler = Scheduler(es)
    engine = Engine.withStepTitleResolver(resolver)
  }

  @TearDown(Level.Trial)
  final def afterAll(): Unit = {
    println("")
    println("Shutting down ExecutionContext...")
    es.shutdown()
  }
  /*
[info] Benchmark                (transitionNumber)   Mode  Cnt      Score     Error  Units
[info] CheckStepBench.runModel                  10  thrpt   10  34220.496 ±  71.293  ops/s
[info] CheckStepBench.runModel                  20  thrpt   10  24294.561 ± 385.750  ops/s
[info] CheckStepBench.runModel                  50  thrpt   10  14380.225 ± 141.274  ops/s
[info] CheckStepBench.runModel                 100  thrpt   10   6306.461 ±  47.306  ops/s
[info] CheckStepBench.runModel                 200  thrpt   10   3302.245 ±  18.204  ops/s
  */

  @Benchmark
  def runModel() = {
    val checkStep = CheckModelStep(maxNumberOfRuns = 1, maxNumberOfTransitions = transitionNumber.toInt, CheckStepBench.modelRunner, None)
    val s = Scenario("scenario with checkStep", checkStep :: Nil)
    val f = engine.runScenario(session)(s)
    val res = Await.result(f.runToFuture(scheduler), Duration.Inf)
    assert(res.isSuccess)
  }

}

object CheckStepBench {
  val resolver = PlaceholderResolver.withoutExtractor()
  val matcherResolver = MatcherResolver()

  def integerGen(rc: RandomContext): ValueGenerator[Int] = ValueGenerator(
    name = "integer",
    gen = () ⇒ rc.seededRandom.nextInt(10000))

  def dummyProperty1(name: String): PropertyN[Int, NoValue, NoValue, NoValue, NoValue, NoValue] =
    Property1(
      description = name,
      invariant = g ⇒ EffectStep.fromSyncE("add generated", _.addValue("generated", g().toString)))

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

