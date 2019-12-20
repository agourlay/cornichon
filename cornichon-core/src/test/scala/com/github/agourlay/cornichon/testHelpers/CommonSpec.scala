package com.github.agourlay.cornichon.testHelpers

import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.dsl.ProvidedInstances
import com.github.agourlay.cornichon.steps.cats.EffectStep
import com.github.agourlay.cornichon.steps.regular.assertStep.{ AssertStep, Assertion }
import utest._

trait CommonSpec extends ProvidedInstances with TaskSpec {
  this: TestSuite =>

  def integerGen(rc: RandomContext): ValueGenerator[Int] = ValueGenerator(
    name = "integer",
    gen = () => rc.nextInt(10000))

  def brokenIntGen(rc: RandomContext): ValueGenerator[Int] = ValueGenerator(
    name = "integer",
    gen = () => throw new RuntimeException(s"boom gen with initial seed ${rc.initialSeed}"))

  def stringGen(rc: RandomContext): ValueGenerator[String] = ValueGenerator(
    name = "integer",
    gen = () => rc.nextString(10))

  val alwaysValidAssertStep = AssertStep("valid", _ => Assertion.alwaysValid)
  val brokenEffect: Step = EffectStep.fromSyncE("always boom", _ => Left(CornichonError.fromString("boom!")))
  val neverValidAssertStep = AssertStep("never valid assert step", _ => Assertion.failWith("never valid!"))
  val identityStep: Step = EffectStep.fromSync("identity effect step", _.session)

  def scenarioFailsWithMessage(report: ScenarioReport)(expectedMessage: String): Unit =
    report match {
      case f: FailureScenarioReport =>
        def clue = f.msg + "\nwith logs\n" + LogInstruction.renderLogs(f.logs) + "\n\n"
        if (f.msg != expectedMessage) {
          println(clue)
        }
        assert(f.msg == expectedMessage)
      case other =>
        println(s"Should have been a FailedScenarioReport but got \n${LogInstruction.renderLogs(other.logs)}")
        assert(false)
    }

  def matchLogsWithoutDuration(logs: List[LogInstruction])(expectedRenderedLogs: String): Unit = {
    val renderedLogs = LogInstruction.renderLogs(logs, colorized = false)
    val cleanedLogs = renderedLogs.split('\n').toList.map { l =>
      // check if duration is present at end
      if (l.nonEmpty && l.last == ']')
        l.dropRight(1) // drop ']'
          .reverse
          .dropWhile(_ != '[') // drop measurement
          .drop(1) // drop '['
          .dropWhile(_ == ' ') // drop whitespaces
          .reverse
      else
        l
    }
    val preparedCleanedLogs = cleanedLogs.mkString("\n")
    assert(preparedCleanedLogs == expectedRenderedLogs)
  }
}
