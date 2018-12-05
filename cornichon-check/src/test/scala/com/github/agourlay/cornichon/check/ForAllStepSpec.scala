package com.github.agourlay.cornichon.check

import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.dsl.ProvidedInstances
import com.github.agourlay.cornichon.resolver.PlaceholderResolver
import com.github.agourlay.cornichon.steps.regular.EffectStep
import com.github.agourlay.cornichon.steps.regular.assertStep.{ AssertStep, Assertion }
import monix.eval.Task
import monix.execution.Scheduler
import org.scalatest.{ AsyncWordSpec, Matchers }

import scala.concurrent.Future

// TODO use StepUtilSpec through some kind of common-testing
class ForAllStepSpec extends AsyncWordSpec with Matchers with ProvidedInstances with CheckDsl {

  implicit def taskToFuture[A](t: Task[A])(implicit s: Scheduler): Future[A] =
    t.runToFuture(s)

  implicit val scheduler = Scheduler.Implicits.global
  val resolver = PlaceholderResolver.withoutExtractor()
  val engine = Engine.withStepTitleResolver(resolver)

  def integerGen(rc: RandomContext): ValueGenerator[Int] = ValueGenerator(
    name = "integer",
    gen = () ⇒ rc.seededRandom.nextInt(10000))

  def brokenIntGen(rc: RandomContext): ValueGenerator[Int] = ValueGenerator(
    name = "integer",
    gen = () ⇒ throw new RuntimeException("boom gen!"))

  val brokenEffect: EffectStep = EffectStep.fromSyncE("always boom", _ ⇒ Left(CornichonError.fromString("boom!")))

  val neverValidAssertStep = AssertStep("never valid assert step", _ ⇒ Assertion.failWith("never valid!"))

  "ForAllStep" when {

    "always terminates" must {

      "with maxNumberOfRuns" in {
        val maxRun = 100
        var uglyCounter = 0
        val incrementEffect: EffectStep = EffectStep.fromSync("identity", s ⇒ { uglyCounter = uglyCounter + 1; s })

        val forAllStep = for_all("fails", maxNumberOfRuns = maxRun, integerGen)(_ ⇒ incrementEffect)
        val s = Scenario("scenario with forAllStep", forAllStep :: Nil)

        engine.runScenario(Session.newEmpty)(s).map {
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

        engine.runScenario(Session.newEmpty)(s).map {
          case f: FailureScenarioReport ⇒
            f.isSuccess should be(false)
            f.msg should be("""Scenario 'scenario with forAllStep' failed:
                              |
                              |at step:
                              |always boom
                              |
                              |with error(s):
                              |boom!
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

        engine.runScenario(Session.newEmpty)(s).map {
          case f: FailureScenarioReport ⇒
            f.isSuccess should be(false)
          case other @ _ ⇒
            fail(s"should have failed but got $other")
        }
      }
    }
  }
}
