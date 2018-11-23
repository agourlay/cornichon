package com.github.agourlay.cornichon.check.examples.scalacheck

import com.github.agourlay.cornichon.CornichonFeature
import com.github.agourlay.cornichon.check._
import com.github.agourlay.cornichon.check.checkModel.{ Model, ModelRunner, Property1 }
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

  def coinGen(rc: RandomContext): ValueGenerator[Coin] = ValueGenerator(
    name = "a Coin",
    genFct = () ⇒ {
      val nextSeed = rc.seededRandom.nextLong()
      val params = Gen.Parameters.default.withInitialSeed(nextSeed)
      val coin = Gen.oneOf[Coin](Head, Tail)
      coin(params, Seed(nextSeed)).get
    }
  )

  val myModelRunner = ModelRunner.make[Coin](coinGen) {

    val entryPoint = Property1[Coin](
      description = "Entry point",
      invariant = _ ⇒ print_step("Start flipping a coin")
    )

    val flipCoin = Property1[Coin](
      description = "Flip coin",
      invariant = coinGen ⇒ print_step(s"Flip result ${coinGen()}")
    )

    Model(
      description = "Flipping a coin model",
      entryPoint = entryPoint,
      transitions = Map(
        entryPoint -> ((1.0, flipCoin) :: Nil),
        flipCoin -> ((1.0, flipCoin) :: Nil)
      )
    )
  }

}

