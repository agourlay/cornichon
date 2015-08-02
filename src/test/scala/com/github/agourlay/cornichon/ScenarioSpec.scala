package com.github.agourlay.cornichon

import com.github.agourlay.cornichon.core._
import org.scalatest.{ Matchers, WordSpec }

import cats.data.Xor
import Xor.{ left, right }

class ScenarioSpec extends WordSpec with Matchers {

  val resolver = new Resolver
  val engine = new Engine(resolver)

  "A scenario" must {
    "execute all steps" in {
      val session = Session.newSession
      val steps = Seq(Step[Int]("first step", s ⇒ (2, s), _ > 0))
      val s = Scenario("test", steps)
      engine.runScenario(s)(session).isRight should be(true)
    }

    "fail if instruction throws exception" in {
      val session = Session.newSession
      val steps = Seq(
        Step[Int]("stupid step", s ⇒ {
          6 / 0
          (2, s)
        }, _ > 0))
      val s = Scenario("scenario with stupid test", steps)
      engine.runScenario(s)(session).isLeft should be(true)
    }

    "stop at first failed step" in {
      val session = Session.newSession
      val step1 = Step[Int]("first step", s ⇒ (2, s), _ > 0)
      val step2 = Step[Int]("second step", s ⇒ (5, s), _ < 0)
      val step3 = Step[Int]("third step", s ⇒ (1, s), _ > 0)
      val steps = Seq(
        step1, step2, step3
      )
      val s = Scenario("test", steps)
      val res = engine.runScenario(s)(session)
      res.isLeft should be(true)
      res.toEither.left.get.failedStep.error.msg should be("step 'second step' did not pass assertion, actual result is '5'")
      res.toEither.left.get.successSteps should be(Seq(step1.title))
      res.toEither.left.get.notExecutedStep should be(Seq(step3.title))
    }
  }

}
