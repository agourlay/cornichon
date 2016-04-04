package com.github.agourlay.cornichon.core

import com.github.agourlay.cornichon.steps.regular.{ AssertStep, DebugStep }
import com.github.agourlay.cornichon.steps.wrapped._
import org.scalatest.{ Matchers, WordSpec }

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

class EngineSpec extends WordSpec with Matchers {

  val engine = new Engine(ExecutionContext.global)

  "An engine" when {
    "runScenario" must {
      "execute all steps of a scenario" in {
        val session = Session.newSession
        val steps = Vector(AssertStep[Int]("first step", s ⇒ SimpleStepAssertion(2 + 1, 3)))
        val s = Scenario("test", steps)
        engine.runScenario(session)(s).isInstanceOf[SuccessScenarioReport] should be(true)
      }

      "fail if instruction throws exception" in {
        val session = Session.newSession
        val steps = Vector(
          AssertStep[Int]("stupid step", s ⇒ {
            6 / 0
            SimpleStepAssertion(2, 2)
          })
        )
        val s = Scenario("scenario with stupid test", steps)
        engine.runScenario(session)(s).isInstanceOf[FailedScenarioReport] should be(true)
      }

      "stop at first failed step" in {
        val session = Session.newSession
        val step1 = AssertStep[Int]("first step", s ⇒ SimpleStepAssertion(2, 2))
        val step2 = AssertStep[Int]("second step", s ⇒ SimpleStepAssertion(4, 5))
        val step3 = AssertStep[Int]("third step", s ⇒ SimpleStepAssertion(1, 1))
        val steps = Vector(
          step1, step2, step3
        )
        val s = Scenario("test", steps)
        engine.runScenario(session)(s) match {
          case s: SuccessScenarioReport ⇒ fail("Should be a FailedScenarioReport")
          case f: FailedScenarioReport ⇒
            f.failedStep.error.msg.replaceAll("\r", "") should be("""
            |expected result was:
            |'4'
            |but actual result is:
            |'5'""".stripMargin.trim)
            f.successSteps should be(Vector(step1.title))
            f.notExecutedStep should be(Vector(step3.title))
        }
      }

      "replay eventually wrapped steps" in {
        val session = Session.newSession
        val eventuallyConf = EventuallyConf(maxTime = 5.seconds, interval = 10.milliseconds)
        val nested: Vector[Step] = Vector(
          AssertStep(
            "possible random value step",
            s ⇒ SimpleStepAssertion(scala.util.Random.nextInt(10), 5)
          )
        )

        val steps = Vector(EventuallyStep(nested, eventuallyConf))
        val s = Scenario("scenario with eventually", steps)
        engine.runScenario(session)(s).isInstanceOf[SuccessScenarioReport] should be(true)
      }

      "replay eventually wrapped steps until limit" in {
        val session = Session.newSession
        val eventuallyConf = EventuallyConf(maxTime = 10.milliseconds, interval = 1.milliseconds)
        val nested: Vector[Step] = Vector(
          AssertStep(
            "impossible random value step", s ⇒ SimpleStepAssertion(11, scala.util.Random.nextInt(10))
          )
        )
        val steps = Vector(
          EventuallyStep(nested, eventuallyConf)
        )
        val s = Scenario("scenario with eventually that fails", steps)
        engine.runScenario(session)(s).isInstanceOf[FailedScenarioReport] should be(true)
      }

      "success if non equality was expected" in {
        val session = Session.newSession
        val steps = Vector(
          AssertStep(
            "non equals step", s ⇒ SimpleStepAssertion(1, 2), negate = true
          )
        )
        val s = Scenario("scenario with unresolved", steps)
        engine.runScenario(session)(s).isInstanceOf[SuccessScenarioReport] should be(true)
      }

      "return error if a Debug step throw an exception" in {
        val session = Session.newSession
        val step = DebugStep(s ⇒ {
          6 / 0
          "Never gonna read this"
        })
        val s = Scenario("scenario with faulty debug step", Vector(step))
        engine.runScenario(session)(s).isInstanceOf[FailedScenarioReport] should be(true)
      }

      "control duration of 'within' wrapped steps" in {
        val session = Session.newSession
        val d = 200.millis
        val nested: Vector[Step] = Vector(
          AssertStep(
            "possible random value step",
            s ⇒ {
              Thread.sleep(100)
              SimpleStepAssertion(true, true)
            }
          )
        )
        val steps = Vector(
          WithinStep(nested, d)
        )
        val s = Scenario("scenario with Within", steps)
        engine.runScenario(session)(s).isInstanceOf[SuccessScenarioReport] should be(true)
      }

      "fail if duration of 'within' is exceeded" in {
        val session = Session.newSession
        val d = 200.millis
        val nested: Vector[Step] = Vector(
          AssertStep(
            "possible random value step",
            s ⇒ {
              Thread.sleep(250)
              SimpleStepAssertion(true, true)
            }
          )
        )
        val steps = Vector(
          WithinStep(nested, d)
        )
        val s = Scenario("scenario with Within", steps)
        engine.runScenario(session)(s).isInstanceOf[SuccessScenarioReport] should be(false)
      }

      "fail if 'repeat' block contains a failed step" in {
        val nested: Vector[Step] = Vector(
          AssertStep(
            "always fails",
            s ⇒ SimpleStepAssertion(true, false)
          )
        )
        val steps = Vector(
          RepeatStep(nested, 5)
        )
        val s = Scenario("scenario with Repeat", steps)
        engine.runScenario(Session.newSession)(s).isInstanceOf[SuccessScenarioReport] should be(false)
      }

      "repeat steps inside a 'repeat' block" in {
        var uglyCounter = 0
        val loop = 5
        val nested: Vector[Step] = Vector(
          AssertStep(
            "increment captured counter",
            s ⇒ {
              uglyCounter = uglyCounter + 1
              SimpleStepAssertion(true, true)
            }
          )
        )
        val steps = Vector(
          RepeatStep(nested, loop)
        )
        val s = Scenario("scenario with Repeat", steps)
        engine.runScenario(Session.newSession)(s).isInstanceOf[SuccessScenarioReport] should be(true)
        uglyCounter should be(loop)
      }

      "fail if 'repeatDuring' block contains a failed step" in {
        val nested: Vector[Step] = Vector(
          AssertStep(
            "always fails",
            s ⇒ SimpleStepAssertion(true, false)
          )
        )
        val steps = Vector(
          RepeatDuringStep(nested, 5.millis)
        )
        val s = Scenario("scenario with RepeatDuring", steps)
        engine.runScenario(Session.newSession)(s).isInstanceOf[SuccessScenarioReport] should be(false)
      }

      "repeat steps inside 'repeatDuring' for at least the duration param" in {
        val nested: Vector[Step] = Vector(
          AssertStep(
            "always valid",
            s ⇒ {
              Thread.sleep(1)
              SimpleStepAssertion(true, true)
            }
          )
        )
        val steps = Vector(
          RepeatDuringStep(nested, 50.millis)
        )
        val s = Scenario("scenario with RepeatDuring", steps)
        val now = System.nanoTime
        engine.runScenario(Session.newSession)(s).isInstanceOf[SuccessScenarioReport] should be(true)
        val executionTime = Duration.fromNanos(System.nanoTime - now)
        executionTime.gt(50.millis) should be(true)
        // empiric values for the upper bound here
        executionTime.lt(55.millis) should be(true)
      }

      "repeat steps inside 'repeatDuring' at least once if they take more time than the duration param" in {
        val nested: Vector[Step] = Vector(
          AssertStep(
            "always valid",
            s ⇒ {
              Thread.sleep(500)
              SimpleStepAssertion(true, true)
            }
          )
        )
        val steps = Vector(
          RepeatDuringStep(nested, 50.millis)
        )
        val s = Scenario("scenario with RepeatDuring", steps)
        val now = System.nanoTime
        engine.runScenario(Session.newSession)(s).isInstanceOf[SuccessScenarioReport] should be(true)
        val executionTime = Duration.fromNanos(System.nanoTime - now)
        //println(executionTime)
        executionTime.gt(50.millis) should be(true)
        // empiric values for the upper bound here
        executionTime.lt(550.millis) should be(true)
      }

      "fail if 'retryMax' block never succeeds" in {
        var uglyCounter = 0
        val loop = 10
        val nested: Vector[Step] = Vector(
          AssertStep(
            "always fails",
            s ⇒ {
              uglyCounter = uglyCounter + 1
              SimpleStepAssertion(true, false)
            }
          )
        )
        val steps = Vector(
          RetryMaxStep(nested, loop)
        )
        val s = Scenario("scenario with RetryMax", steps)
        engine.runScenario(Session.newSession)(s).isInstanceOf[SuccessScenarioReport] should be(false)
        // Initial run + 'loop' retries
        uglyCounter should be(loop + 1)
      }

      "repeat 'retryMax' and might succeed later" in {
        var uglyCounter = 0
        val max = 10
        val nested: Vector[Step] = Vector(
          AssertStep(
            "always fails",
            s ⇒ {
              uglyCounter = uglyCounter + 1
              SimpleStepAssertion(true, uglyCounter == max - 2)
            }
          )
        )
        val steps = Vector(
          RetryMaxStep(nested, max)
        )
        val s = Scenario("scenario with RetryMax", steps)
        engine.runScenario(Session.newSession)(s).isInstanceOf[SuccessScenarioReport] should be(true)
        uglyCounter should be(max - 2)
      }

    }

    "runStepPredicate" must {
      "return session if success" in {
        val session = Session.newSession
        val step = AssertStep[Int]("stupid step", s ⇒ {
          6 / 0
          SimpleStepAssertion(2, 2)
        })
        engine.runStepPredicate(negateStep = false, session, SimpleStepAssertion(true, true)).fold(e ⇒ fail("should have been Right"), s ⇒ s should be(session))
      }
    }
  }
}
