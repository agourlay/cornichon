package com.github.agourlay.cornichon.core

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
        val steps = Vector(
          EventuallyStart(eventuallyConf),
          AssertStep(
            "possible random value step",
            s ⇒ SimpleStepAssertion(scala.util.Random.nextInt(10), 5)
          ),
          EventuallyStop
        )
        val s = Scenario("scenario with eventually", steps)
        engine.runScenario(session)(s).isInstanceOf[SuccessScenarioReport] should be(true)
      }

      "replay eventually wrapped steps until limit" in {
        val session = Session.newSession
        val eventuallyConf = EventuallyConf(maxTime = 10.milliseconds, interval = 1.milliseconds)
        val steps = Vector(
          EventuallyStart(eventuallyConf),
          AssertStep(
            "impossible random value step", s ⇒ SimpleStepAssertion(11, scala.util.Random.nextInt(10))
          ),
          EventuallyStop
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
        val steps = Vector(
          WithinStart(d),
          AssertStep(
            "possible random value step",
            s ⇒ {
              Thread.sleep(100)
              SimpleStepAssertion(true, true)
            }
          ),
          WithinStop
        )
        val s = Scenario("scenario with Within", steps)
        engine.runScenario(session)(s).isInstanceOf[SuccessScenarioReport] should be(true)
      }

      "fail if duration of 'within' is exceeded" in {
        val session = Session.newSession
        val d = 200.millis
        val steps = Vector(
          WithinStart(d),
          AssertStep(
            "possible random value step",
            s ⇒ {
              Thread.sleep(250)
              SimpleStepAssertion(true, true)
            }
          ),
          WithinStop
        )
        val s = Scenario("scenario with Within", steps)
        engine.runScenario(session)(s).isInstanceOf[SuccessScenarioReport] should be(false)
      }

      "fail if 'repeat' block contains a failed step" in {
        val steps = Vector(
          RepeatStart(5),
          AssertStep(
            "always fails",
            s ⇒ SimpleStepAssertion(true, false)
          ),
          RepeatStop
        )
        val s = Scenario("scenario with Repeat", steps)
        engine.runScenario(Session.newSession)(s).isInstanceOf[SuccessScenarioReport] should be(false)
      }

      "repeat steps inside a 'repeat' block" in {
        var uglyCounter = 0
        val loop = 5
        val steps = Vector(
          RepeatStart(loop),
          AssertStep(
            "increment captured counter",
            s ⇒ {
              uglyCounter = uglyCounter + 1
              SimpleStepAssertion(true, true)
            }
          ),
          RepeatStop
        )
        val s = Scenario("scenario with Repeat", steps)
        engine.runScenario(Session.newSession)(s).isInstanceOf[SuccessScenarioReport] should be(true)
        uglyCounter should be(loop)
      }

      "fail if 'repeatDuring' block contains a failed step" in {
        val steps = Vector(
          RepeatDuringStart(5.millis),
          AssertStep(
            "always fails",
            s ⇒ SimpleStepAssertion(true, false)
          ),
          RepeatDuringStop
        )
        val s = Scenario("scenario with RepeatDuring", steps)
        engine.runScenario(Session.newSession)(s).isInstanceOf[SuccessScenarioReport] should be(false)
      }

      "repeat steps inside 'repeatDuring' for at least the duration param" in {
        val steps = Vector(
          RepeatDuringStart(50.millis),
          AssertStep(
            "always valid",
            s ⇒ {
              Thread.sleep(1)
              SimpleStepAssertion(true, true)
            }
          ),
          RepeatDuringStop
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
        val steps = Vector(
          RepeatDuringStart(50.millis),
          AssertStep(
            "always valid",
            s ⇒ {
              Thread.sleep(500)
              SimpleStepAssertion(true, true)
            }
          ),
          RepeatDuringStop
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
        val steps = Vector(
          RetryMaxStart(loop),
          AssertStep(
            "always fails",
            s ⇒ {
              uglyCounter = uglyCounter + 1
              SimpleStepAssertion(true, false)
            }
          ),
          RetryMaxStop
        )
        val s = Scenario("scenario with RetryMax", steps)
        engine.runScenario(Session.newSession)(s).isInstanceOf[SuccessScenarioReport] should be(false)
        // Initial run + 'loop' retries
        uglyCounter should be(loop + 1)
      }

      "repeat 'retryMax' and might succeed later" in {
        var uglyCounter = 0
        val max = 10
        val steps = Vector(
          RetryMaxStart(max),
          AssertStep(
            "always fails",
            s ⇒ {
              uglyCounter = uglyCounter + 1
              SimpleStepAssertion(true, uglyCounter == max - 2)
            }
          ),
          RetryMaxStop
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

    "findEnclosedStep" must {
      "resolve non nested sub steps" in {
        val eventuallyConf = EventuallyConf(maxTime = 10.milliseconds, interval = 1.milliseconds)
        val steps = Vector(
          EventuallyStart(eventuallyConf),
          AssertStep[Int]("first step", s ⇒ SimpleStepAssertion(2 + 1, 3)),
          AssertStep[Int]("second step", s ⇒ SimpleStepAssertion(2 + 1, 3)),
          EventuallyStop
        )
        engine.findEnclosedSteps(steps.head, steps.tail).size should be(2)
      }

      "resolve non nested aligned sub steps" in {
        val eventuallyConf = EventuallyConf(maxTime = 10.milliseconds, interval = 1.milliseconds)
        val steps = Vector(
          EventuallyStart(eventuallyConf),
          AssertStep[Int]("first step", s ⇒ SimpleStepAssertion(2 + 1, 3)),
          AssertStep[Int]("second step", s ⇒ SimpleStepAssertion(2 + 1, 3)),
          EventuallyStop,
          EventuallyStart(eventuallyConf),
          AssertStep[Int]("third step", s ⇒ SimpleStepAssertion(2 + 1, 3)),
          EventuallyStop
        )
        engine.findEnclosedSteps(steps.head, steps.tail).size should be(2)
      }

      "resolve nested sub steps" in {
        val eventuallyConf = EventuallyConf(maxTime = 10.milliseconds, interval = 1.milliseconds)
        val steps = Vector(
          EventuallyStart(eventuallyConf),
          AssertStep[Int]("first step", s ⇒ SimpleStepAssertion(2 + 1, 3)),
          AssertStep[Int]("second step", s ⇒ SimpleStepAssertion(2 + 1, 3)),
          EventuallyStart(eventuallyConf),
          AssertStep[Int]("third step", s ⇒ SimpleStepAssertion(2 + 1, 3)),
          EventuallyStop,
          EventuallyStop
        )
        engine.findEnclosedSteps(steps.head, steps.tail).size should be(5)
      }
    }
  }
}
