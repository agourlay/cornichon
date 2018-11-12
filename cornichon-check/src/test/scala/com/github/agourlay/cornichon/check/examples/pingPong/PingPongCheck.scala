package com.github.agourlay.cornichon.check.examples.pingPong

import com.github.agourlay.cornichon.CornichonFeature
import com.github.agourlay.cornichon.check._

class PingPongCheck extends CornichonFeature with CheckDsl {

  def feature = Feature("Basic examples of checks") {

    Scenario("ping pong check") {

      Given I check_model(maxNumberOfRuns = 2, maxNumberOfTransitions = 10)(myModelRunner)

    }
  }

  def stringGen(rc: RandomContext): ValueGenerator[String] = ValueGenerator(
    name = "an alphanumeric String",
    genFct = () ⇒ rc.seededRandom.alphanumeric.take(20).mkString(""))

  def integerGen(rc: RandomContext): ValueGenerator[Int] = ValueGenerator(
    name = "integer",
    genFct = () ⇒ rc.seededRandom.nextInt(10000))

  val myModelRunner = ModelRunner.make[String, Int](stringGen, integerGen) {

    val entryPoint = Property2[String, Int](
      description = "Entry point",
      invariant = (_, _) ⇒ print_step("Start game")
    )

    val pingString = Property2[String, Int](
      description = "Ping String",
      invariant = (stringGen, _) ⇒ print_step(s"Ping ${stringGen()}")
    )

    val pongInt = Property2[String, Int](
      description = "Pong Int",
      invariant = (_, intGen) ⇒ print_step(s"Pong ${intGen()}")
    )

    val exitPoint = Property2[String, Int](
      description = "Exit point",
      invariant = (_, _) ⇒ print_step("End of game")
    )

    Model(
      description = "ping pong model",
      entryPoint = entryPoint,
      transitions = Map(
        entryPoint -> ((0.5, pingString) :: (0.5, pongInt) :: Nil),
        pingString -> ((0.9, pongInt) :: (0.1, exitPoint) :: Nil),
        pongInt -> ((0.9, pingString) :: (0.1, exitPoint) :: Nil)
      )
    )
  }

}
