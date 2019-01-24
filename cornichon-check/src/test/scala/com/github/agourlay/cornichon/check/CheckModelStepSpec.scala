package com.github.agourlay.cornichon.check

import com.github.agourlay.cornichon.check.checkModel._
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
class CheckModelStepSpec extends AsyncWordSpec with Matchers with ProvidedInstances {

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

  val identityStep: EffectStep = EffectStep.fromSync("identity effect step", identity)

  def dummyProperty1(name: String, preNeverValid: Boolean = false, step: Step = identityStep, callGen: Boolean = false): PropertyN[Int, NoValue, NoValue, NoValue, NoValue, NoValue] =
    Property1(
      description = name,
      preCondition = if (preNeverValid) neverValidAssertStep else identityStep,
      invariant = g ⇒ if (callGen) { g(); step } else step)

  "CheckModelStep" when {

    "validate transitions definition" must {

      "detect empty transition for starting property" in {
        val starting = dummyProperty1("starting property")
        val otherAction = dummyProperty1("other property")
        val transitions = Map(otherAction -> ((100, starting) :: Nil))
        val model = Model("model with empty transition for starting property", starting, transitions)
        val modelRunner = ModelRunner.make(integerGen)(model)
        val seed = 1L
        val checkStep = CheckModelStep(10, 10, modelRunner, Some(seed))
        val s = Scenario("scenario with checkStep", checkStep :: Nil)

        engine.runScenario(Session.newEmpty)(s).map {
          case f: FailureScenarioReport ⇒
            f.isSuccess should be(false)
            f.msg should be("""Scenario 'scenario with checkStep' failed:
                              |
                              |at step:
                              |Checking model 'model with empty transition for starting property' with maxNumberOfRuns=10 and maxNumberOfTransitions=10 and seed=1
                              |
                              |with error(s):
                              |No outgoing transitions definition found for starting property 'starting property'
                              |""".stripMargin)
          case other @ _ ⇒
            fail(s"should have failed but got $other")
        }
      }

      "detect duplicate transition to target" in {
        val starting = dummyProperty1("starting property")
        val otherAction = dummyProperty1("other property")
        val transitions = Map(
          starting -> ((100, otherAction) :: Nil),
          otherAction -> ((80, starting) :: (20, starting) :: Nil))
        val model = Model("model with empty transition for starting", starting, transitions)
        val modelRunner = ModelRunner.make(integerGen)(model)
        val seed = 1L
        val checkStep = CheckModelStep(10, 10, modelRunner, Some(seed))
        val s = Scenario("scenario with checkStep", checkStep :: Nil)

        engine.runScenario(Session.newEmpty)(s).map {
          case f: FailureScenarioReport ⇒
            f.isSuccess should be(false)
            f.msg should be("""Scenario 'scenario with checkStep' failed:
                              |
                              |at step:
                              |Checking model 'model with empty transition for starting' with maxNumberOfRuns=10 and maxNumberOfTransitions=10 and seed=1
                              |
                              |with error(s):
                              |Transitions definition from 'other property' contains duplicates target properties
                              |""".stripMargin)
          case other @ _ ⇒
            fail(s"should have failed but got $other")
        }
      }

      "detect incorrect weigh definition" in {
        val starting = dummyProperty1("starting property")
        val otherAction = dummyProperty1("other property")
        val transitions = Map(
          starting -> ((100, otherAction) :: Nil),
          otherAction -> ((101, starting) :: Nil))
        val model = Model("model with empty transition for starting", starting, transitions)
        val modelRunner = ModelRunner.make(integerGen)(model)
        val seed = 1L
        val checkStep = CheckModelStep(10, 10, modelRunner, Some(seed))
        val s = Scenario("scenario with checkStep", checkStep :: Nil)

        engine.runScenario(Session.newEmpty)(s).map {
          case f: FailureScenarioReport ⇒
            f.isSuccess should be(false)
            f.msg should be("""Scenario 'scenario with checkStep' failed:
                              |
                              |at step:
                              |Checking model 'model with empty transition for starting' with maxNumberOfRuns=10 and maxNumberOfTransitions=10 and seed=1
                              |
                              |with error(s):
                              |Transitions definition from 'other property' contains incorrect weight definition (above 100)
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
        val incrementEffect: EffectStep = EffectStep.fromSync("identity", s ⇒ { uglyCounter = uglyCounter + 1; s })

        val starting = dummyProperty1("starting property", step = incrementEffect)
        val otherAction = dummyProperty1("other property")
        val transitions = Map(starting -> ((100, otherAction) :: Nil))
        val model = Model("model with empty transition for starting", starting, transitions)
        val modelRunner = ModelRunner.make(integerGen)(model)
        val seed = 1L
        val checkStep = CheckModelStep(maxNumberOfRuns = maxRun, 1, modelRunner, Some(seed))
        val s = Scenario("scenario with checkStep", checkStep :: Nil)

        engine.runScenario(Session.newEmpty)(s).map {
          case f: SuccessScenarioReport ⇒
            f.isSuccess should be(true)
            uglyCounter should be(maxRun)

          case other @ _ ⇒
            fail(s"should have succeeded but got $other")
        }
      }

      "with maxNumberOfTransitions (even with cyclic model)" in {
        val maxTransition = 100
        var uglyCounter = 0
        val incrementEffect: EffectStep = EffectStep.fromSync("identity", s ⇒ { uglyCounter = uglyCounter + 1; s })

        val starting = dummyProperty1("starting property")
        val otherAction = dummyProperty1("other property", step = incrementEffect)
        val otherActionTwo = dummyProperty1("other property two ", step = incrementEffect)
        val transitions = Map(
          starting -> ((100, otherAction) :: Nil),
          otherAction -> ((100, otherActionTwo) :: Nil),
          otherActionTwo -> ((100, otherAction) :: Nil))
        val model = Model("model with empty transition for starting", starting, transitions)
        val modelRunner = ModelRunner.make(integerGen)(model)
        val seed = 1L
        val checkStep = CheckModelStep(maxNumberOfRuns = 1, maxTransition, modelRunner, Some(seed))
        val s = Scenario("scenario with checkStep", checkStep :: Nil)

        engine.runScenario(Session.newEmpty)(s).map {
          case f: SuccessScenarioReport ⇒
            f.isSuccess should be(true)
            uglyCounter should be(maxTransition)

          case other @ _ ⇒
            fail(s"should have succeeded but got $other")
        }
      }
    }

    "report failure" must {

      "an action explodes" in {
        val starting = dummyProperty1("starting property")
        val otherAction = dummyProperty1("other property", step = brokenEffect)
        val transitions = Map(
          starting -> ((100, otherAction) :: Nil),
          otherAction -> ((100, starting) :: Nil))
        val model = Model("model with empty transition for starting", starting, transitions)
        val modelRunner = ModelRunner.make(integerGen)(model)
        val seed = 1L
        val checkStep = CheckModelStep(maxNumberOfRuns = 10, 10, modelRunner, Some(seed))
        val s = Scenario("scenario with checkStep", checkStep :: Nil)

        engine.runScenario(Session.newEmpty)(s).map {
          case f: FailureScenarioReport ⇒
            f.isSuccess should be(false)
            f.msg should be("""Scenario 'scenario with checkStep' failed:
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

      "no pre conditions are valid" in {
        val starting = dummyProperty1("starting property")
        val otherAction = dummyProperty1("other property", preNeverValid = true)
        val transitions = Map(
          starting -> ((100, otherAction) :: Nil),
          otherAction -> ((100, starting) :: Nil))
        val model = Model("model with empty transition for starting", starting, transitions)
        val modelRunner = ModelRunner.make(integerGen)(model)
        val seed = 1L
        val checkStep = CheckModelStep(maxNumberOfRuns = 10, 10, modelRunner, Some(seed))
        val s = Scenario("scenario with checkStep", checkStep :: Nil)

        engine.runScenario(Session.newEmpty)(s).map {
          case f: FailureScenarioReport ⇒
            f.isSuccess should be(false)
            f.msg should be("""Scenario 'scenario with checkStep' failed:
                              |
                              |at step:
                              |Checking model 'model with empty transition for starting' with maxNumberOfRuns=10 and maxNumberOfTransitions=10 and seed=1
                              |
                              |with error(s):
                              |No outgoing transition found from `starting property` to another property with valid pre-conditions
                              |""".stripMargin)
          case other @ _ ⇒
            fail(s"should have failed but got $other")
        }
      }
    }

    "generators" must {

      "not using a generator should really not call it" in {
        val starting = dummyProperty1("starting property")
        val otherAction = dummyProperty1("other property")
        val transitions = Map(
          starting -> ((100, otherAction) :: Nil),
          otherAction -> ((100, starting) :: Nil))
        val model = Model("model with empty transition for starting", starting, transitions)
        // passing a broken gen but the actions are not calling it...should be good!
        val modelRunner = ModelRunner.make(brokenIntGen)(model)
        val seed = 1L
        val checkStep = CheckModelStep(maxNumberOfRuns = 10, 10, modelRunner, Some(seed))
        val s = Scenario("scenario with checkStep", checkStep :: Nil)

        engine.runScenario(Session.newEmpty)(s).map {
          case f: SuccessScenarioReport ⇒
            f.isSuccess should be(true)

          case other @ _ ⇒
            fail(s"should have succeeded but got $other")
        }
      }

      "fail the test if the gen throws" in {
        val starting = dummyProperty1("starting property")
        val otherAction = dummyProperty1("other property", callGen = true)
        val transitions = Map(
          starting -> ((100, otherAction) :: Nil),
          otherAction -> ((100, starting) :: Nil))
        val model = Model("model with empty transition for starting", starting, transitions)
        val modelRunner = ModelRunner.make(brokenIntGen)(model)
        val seed = 1L
        val checkStep = CheckModelStep(maxNumberOfRuns = 10, 10, modelRunner, Some(seed))
        val s = Scenario("scenario with checkStep", checkStep :: Nil)

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
