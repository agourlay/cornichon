package com.github.agourlay.cornichon.check.examples.scalacheck

import com.github.agourlay.cornichon.CornichonFeature
import com.github.agourlay.cornichon.check._
import com.github.agourlay.cornichon.check.checkModel.{ Model, ModelRunner, Property1 }
import com.github.agourlay.cornichon.core.{ NoOpStep, RandomContext }
import com.github.agourlay.cornichon.steps.regular.assertStep.{ AssertStep, GenericEqualityAssertion }
import org.scalacheck.Gen
import org.scalacheck.rng.Seed

class ScalacheckIntegration extends CornichonFeature with CheckDsl {

  def feature = Feature("Basic examples of scalacheck integration") {

    Scenario("Scalacheck integration") {

      Given I check_model(maxNumberOfRuns = 2, maxNumberOfTransitions = 10)(myModelRunner)

    }
  }

  sealed trait Coin
  case object Head extends Coin
  case object Tail extends Coin

  def coinGen(rc: RandomContext): Generator[Coin] = OptionalValueGenerator(
    name = "a Coin",
    gen = () ⇒ {
      val nextSeed = rc.seededRandom.nextLong()
      val params = Gen.Parameters.default.withInitialSeed(nextSeed)
      val coin = Gen.oneOf[Coin](Head, Tail)
      coin(params, Seed(nextSeed))
    }
  )

  def assert_head_or_tail(coin: Coin) = AssertStep(
    title = "Head or Tail",
    action = _ ⇒ {
      val coinStr = coin.toString
      val isHead = GenericEqualityAssertion("Head", coinStr)
      val isTail = GenericEqualityAssertion("Tail", coinStr)
      isHead.or(isTail)
    }
  )

  val myModelRunner = ModelRunner.make[Coin](coinGen) {

    val entryPoint = Property1[Coin](
      description = "Entry point",
      invariant = _ ⇒ NoOpStep
    )

    val flipCoin = Property1[Coin](
      description = "Flip coin",
      invariant = coinGen ⇒ assert_head_or_tail(coinGen())
    )

    Model(
      description = "Flipping a coin model",
      entryPoint = entryPoint,
      transitions = Map(
        entryPoint -> ((100, flipCoin) :: Nil),
        flipCoin -> ((100, flipCoin) :: Nil)
      )
    )
  }

}

