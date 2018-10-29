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
class CheckStepSpec extends AsyncWordSpec with Matchers with ProvidedInstances {

  implicit def taskToFuture[A](t: Task[A])(implicit s: Scheduler): Future[A] =
    t.runToFuture(s)

  implicit val scheduler = Scheduler.Implicits.global
  val resolver = PlaceholderResolver.withoutExtractor()
  val engine = Engine.withStepTitleResolver(resolver)

  def integerGen(rc: RandomContext): ValueGenerator[Int] = ValueGenerator(
    name = "integer",
    genFct = () ⇒ rc.seededRandom.nextInt(10000))

  def brokenIntGen(rc: RandomContext): ValueGenerator[Int] = ValueGenerator(
    name = "integer",
    genFct = () ⇒ throw new RuntimeException("boom gen!"))

  val identityEffect: EffectStep = EffectStep.fromSync("identity", identity)

  val brokenEffect: EffectStep = EffectStep.fromSyncE("always boom", _ ⇒ Left(CornichonError.fromString("boom!")))

  val neverValidAssertStep = AssertStep("never valid assert step", _ ⇒ Assertion.failWith("never valid!"))

  def dummyAction1(name: String, preNeverValid: Boolean = false, postNeverValid: Boolean = false, effectStep: EffectStep = identityEffect, callGen: Boolean = false): ActionN[Int, NoValue, NoValue, NoValue, NoValue, NoValue] =
    Action1(
      description = name,
      preConditions = if (preNeverValid) neverValidAssertStep :: Nil else Nil,
      effect = g ⇒ if (callGen) { g(); effectStep } else effectStep,
      postConditions = if (postNeverValid) neverValidAssertStep :: Nil else Nil)

  "CheckStep" when {

    "validate transitions definition" must {

      "detect empty transition for starting action" in {
        val starting = dummyAction1("starting action")
        val otherAction = dummyAction1("other action")
        val transitions = Map(otherAction -> ((1.0, starting) :: Nil))
        val model = Model("model with empty transition for starting", starting, transitions)
        val modelRunner = ModelRunner.make(integerGen)(model)
        val seed = 1L
        val checkStep = CheckStep(10, 10, modelRunner, Some(seed))
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
                              |No outgoing transitions definition found for starting action 'starting action'
                              |""".stripMargin)
          case other @ _ ⇒
            fail(s"should have failed but got $other")
        }
      }

      "detect duplicate transition to target" in {
        val starting = dummyAction1("starting action")
        val otherAction = dummyAction1("other action")
        val transitions = Map(
          starting -> ((1.0, otherAction) :: Nil),
          otherAction -> ((0.8, starting) :: (0.2, starting) :: Nil))
        val model = Model("model with empty transition for starting", starting, transitions)
        val modelRunner = ModelRunner.make(integerGen)(model)
        val seed = 1L
        val checkStep = CheckStep(10, 10, modelRunner, Some(seed))
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
                              |Transitions definition from 'other action' contains duplicates target action
                              |""".stripMargin)
          case other @ _ ⇒
            fail(s"should have failed but got $other")
        }
      }

      "detect incorrect weigh definition" in {
        val starting = dummyAction1("starting action")
        val otherAction = dummyAction1("other action")
        val transitions = Map(
          starting -> ((1.0, otherAction) :: Nil),
          otherAction -> ((1.1, starting) :: Nil))
        val model = Model("model with empty transition for starting", starting, transitions)
        val modelRunner = ModelRunner.make(integerGen)(model)
        val seed = 1L
        val checkStep = CheckStep(10, 10, modelRunner, Some(seed))
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
                              |Transitions definition from 'other action' contains incorrect weight definition (above 1.0)
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

        val starting = dummyAction1("starting action", effectStep = incrementEffect)
        val otherAction = dummyAction1("other action")
        val transitions = Map(starting -> ((1.0, otherAction) :: Nil))
        val model = Model("model with empty transition for starting", starting, transitions)
        val modelRunner = ModelRunner.make(integerGen)(model)
        val seed = 1L
        val checkStep = CheckStep(maxNumberOfRuns = maxRun, 1, modelRunner, Some(seed))
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
        val maxRun = 100
        var uglyCounter = 0
        val incrementEffect: EffectStep = EffectStep.fromSync("identity", s ⇒ { uglyCounter = uglyCounter + 1; s })

        val starting = dummyAction1("starting action", effectStep = incrementEffect)
        val otherAction = dummyAction1("other action", effectStep = incrementEffect)
        val transitions = Map(
          starting -> ((1.0, otherAction) :: Nil),
          otherAction -> ((1.0, starting) :: Nil))
        val model = Model("model with empty transition for starting", starting, transitions)
        val modelRunner = ModelRunner.make(integerGen)(model)
        val seed = 1L
        val checkStep = CheckStep(maxNumberOfRuns = 1, maxRun, modelRunner, Some(seed))
        val s = Scenario("scenario with checkStep", checkStep :: Nil)

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

      "an action explodes" in {
        val starting = dummyAction1("starting action")
        val otherAction = dummyAction1("other action", effectStep = brokenEffect)
        val transitions = Map(
          starting -> ((1.0, otherAction) :: Nil),
          otherAction -> ((1.0, starting) :: Nil))
        val model = Model("model with empty transition for starting", starting, transitions)
        val modelRunner = ModelRunner.make(integerGen)(model)
        val seed = 1L
        val checkStep = CheckStep(maxNumberOfRuns = 10, 10, modelRunner, Some(seed))
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

      "a post condition is broken" in {
        val starting = dummyAction1("starting action")
        val otherAction = dummyAction1("other action", postNeverValid = true)
        val transitions = Map(
          starting -> ((1.0, otherAction) :: Nil),
          otherAction -> ((1.0, starting) :: Nil))
        val model = Model("model with empty transition for starting", starting, transitions)
        val modelRunner = ModelRunner.make(integerGen)(model)
        val seed = 1L
        val checkStep = CheckStep(maxNumberOfRuns = 10, 10, modelRunner, Some(seed))
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
                              |A post-condition was broken for `other action`
                              |caused by:
                              |never valid!
                              |""".stripMargin)
          case other @ _ ⇒
            fail(s"should have failed but got $other")
        }
      }

      "no pre conditions are valid" in {
        val starting = dummyAction1("starting action")
        val otherAction = dummyAction1("other action", preNeverValid = true)
        val transitions = Map(
          starting -> ((1.0, otherAction) :: Nil),
          otherAction -> ((1.0, starting) :: Nil))
        val model = Model("model with empty transition for starting", starting, transitions)
        val modelRunner = ModelRunner.make(integerGen)(model)
        val seed = 1L
        val checkStep = CheckStep(maxNumberOfRuns = 10, 10, modelRunner, Some(seed))
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
                              |No outgoing transition found from `starting action` to another action with valid pre-conditions
                              |""".stripMargin)
          case other @ _ ⇒
            fail(s"should have failed but got $other")
        }
      }
    }

    "generators" must {

      "not using a generator should really not call it" in {
        val starting = dummyAction1("starting action")
        val otherAction = dummyAction1("other action")
        val transitions = Map(
          starting -> ((1.0, otherAction) :: Nil),
          otherAction -> ((1.0, starting) :: Nil))
        val model = Model("model with empty transition for starting", starting, transitions)
        // passing a broken gen but the actions are not calling it...should be good!
        val modelRunner = ModelRunner.make(brokenIntGen)(model)
        val seed = 1L
        val checkStep = CheckStep(maxNumberOfRuns = 10, 10, modelRunner, Some(seed))
        val s = Scenario("scenario with checkStep", checkStep :: Nil)

        engine.runScenario(Session.newEmpty)(s).map {
          case f: SuccessScenarioReport ⇒
            f.isSuccess should be(true)

          case other @ _ ⇒
            fail(s"should have succeeded but got $other")
        }
      }

      "fail the test if the gen throws" in {
        val starting = dummyAction1("starting action")
        val otherAction = dummyAction1("other action", callGen = true)
        val transitions = Map(
          starting -> ((1.0, otherAction) :: Nil),
          otherAction -> ((1.0, starting) :: Nil))
        val model = Model("model with empty transition for starting", starting, transitions)
        val modelRunner = ModelRunner.make(brokenIntGen)(model)
        val seed = 1L
        val checkStep = CheckStep(maxNumberOfRuns = 10, 10, modelRunner, Some(seed))
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
