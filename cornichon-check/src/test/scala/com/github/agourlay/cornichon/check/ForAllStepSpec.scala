package com.github.agourlay.cornichon.check

import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.dsl.ProvidedInstances
import com.github.agourlay.cornichon.steps.cats.EffectStep
import com.github.agourlay.cornichon.steps.regular.assertStep.{ AssertStep, Assertion, GenericEqualityAssertion }
import com.github.agourlay.cornichon.steps.wrapped.AttachStep
import monix.eval.Task
import monix.execution.Scheduler
import org.scalatest.{ AsyncWordSpec, Matchers }

import scala.concurrent.Future

// TODO use StepUtilSpec through some kind of common-testing
class ForAllStepSpec extends AsyncWordSpec with Matchers with ProvidedInstances with CheckDsl {

  implicit def taskToFuture[A](t: Task[A])(implicit s: Scheduler): Future[A] =
    t.runToFuture(s)

  implicit val scheduler = Scheduler.Implicits.global

  def integerGen(rc: RandomContext): ValueGenerator[Int] = ValueGenerator(
    name = "integer",
    gen = () ⇒ rc.seededRandom.nextInt(10000))

  def stringGen(rc: RandomContext): ValueGenerator[String] = ValueGenerator(
    name = "integer",
    gen = () ⇒ rc.seededRandom.nextString(10))

  def brokenIntGen(rc: RandomContext): ValueGenerator[Int] = ValueGenerator(
    name = "integer",
    gen = () ⇒ throw new RuntimeException("boom gen!"))

  val brokenEffect: Step = EffectStep.fromSyncE("always boom", _ ⇒ Left(CornichonError.fromString("boom!")))

  val neverValidAssertStep = AssertStep("never valid assert step", _ ⇒ Assertion.failWith("never valid!"))
  val alwaysValidAssertStep = AssertStep("valid", _ ⇒ Assertion.alwaysValid)

  "ForAllStep" when {

    "validate invariant" must {

      "correct case" in {
        val maxRun = 10

        val forAllStep = for_all("double reverse", maxNumberOfRuns = maxRun, stringGen) { s ⇒
          AssertStep("double reverse string", _ ⇒ GenericEqualityAssertion(s, s.reverse.reverse))
        }
        val s = Scenario("scenario with forAllStep", forAllStep :: Nil)

        ScenarioRunner.runScenario(Session.newEmpty)(s).map {
          case f: SuccessScenarioReport ⇒
            f.isSuccess should be(true)

          case other @ _ ⇒
            fail(s"should have succeeded but got $other")
        }
      }

      "incorrect case" in {
        val maxRun = 10
        var uglyCounter = 0
        val incrementEffect: Step = EffectStep.fromSync("identity", s ⇒ { uglyCounter = uglyCounter + 1; s })

        val forAllStep = for_all("weird case", maxNumberOfRuns = maxRun, integerGen) { _ ⇒
          val assert = if (uglyCounter < 5) alwaysValidAssertStep else brokenEffect
          AttachStep(_ ⇒ incrementEffect :: assert :: Nil)
        }
        val s = Scenario("scenario with forAllStep", forAllStep :: Nil)

        ScenarioRunner.runScenario(Session.newEmpty)(s).map {
          case f: FailureScenarioReport ⇒
            f.isSuccess should be(false)
            uglyCounter should be(6)
            f.msg should be("""Scenario 'scenario with forAllStep' failed:
                              |
                              |at step:
                              |always boom
                              |
                              |with error(s):
                              |boom!
                              |
                              |seed for the run was '1'
                              |""".stripMargin)
          case other @ _ ⇒
            fail(s"should have failed but got $other")
        }
      }

    }

    "always terminates" must {

      "with maxNumberOfRuns" in {
        val maxRun = 100
        var uglyCounter = 0
        val incrementEffect: Step = EffectStep.fromSync("identity", s ⇒ { uglyCounter = uglyCounter + 1; s })

        val forAllStep = for_all("fails", maxNumberOfRuns = maxRun, integerGen)(_ ⇒ incrementEffect)
        val s = Scenario("scenario with forAllStep", forAllStep :: Nil)

        ScenarioRunner.runScenario(Session.newEmpty)(s).map {
          case f: SuccessScenarioReport ⇒
            f.isSuccess should be(true)
            uglyCounter should be(maxRun)

          case other @ _ ⇒
            fail(s"should have succeeded but got $other")
        }
      }
    }

    "report failure" must {

      "an nested step explodes" in {
        val forAllStep = for_all("fails", maxNumberOfRuns = 10, integerGen)(_ ⇒ brokenEffect)
        val s = Scenario("scenario with forAllStep", forAllStep :: Nil)

        ScenarioRunner.runScenario(Session.newEmpty)(s).map {
          case f: FailureScenarioReport ⇒
            f.isSuccess should be(false)
            f.msg should be("""Scenario 'scenario with forAllStep' failed:
                              |
                              |at step:
                              |always boom
                              |
                              |with error(s):
                              |boom!
                              |
                              |seed for the run was '1'
                              |""".stripMargin)
          case other @ _ ⇒
            fail(s"should have failed but got $other")
        }
      }
    }

    "generators" must {

      "fail the test if the gen throws" in {
        val forAllStep = for_all("fails", maxNumberOfRuns = 10, brokenIntGen)(_ ⇒ neverValidAssertStep)
        val s = Scenario("scenario with forAllStep", forAllStep :: Nil)

        ScenarioRunner.runScenario(Session.newEmpty)(s).map {
          case f: FailureScenarioReport ⇒
            f.isSuccess should be(false)
          case other @ _ ⇒
            fail(s"should have failed but got $other")
        }
      }
    }
  }
}
