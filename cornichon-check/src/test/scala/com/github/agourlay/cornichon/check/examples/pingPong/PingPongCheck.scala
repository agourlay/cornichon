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

    val entryPoint = Action2[String, Int](
      description = "Entry point",
      effect = (stringGen, intGen) ⇒ print_step("Start game"),
      postConditions = Nil
    )

    val pingString = Action2[String, Int](
      description = "Ping String",
      effect = (stringGen, intGen) ⇒ print_step(s"Ping ${stringGen()}"),
      postConditions = Nil
    )

    val pongInt = Action2[String, Int](
      description = "Pong Int",
      effect = (stringGen, intGen) ⇒ print_step(s"Pong ${intGen()}"),
      postConditions = Nil
    )

    val exitPoint = Action2[String, Int](
      description = "Exit point",
      effect = (stringGen, intGen) ⇒ print_step("End of game"),
      postConditions = Nil
    )

    Model(
      description = "our first model",
      startingAction = entryPoint,
      transitions = Map(
        entryPoint -> ((0.5, pingString) :: (0.5, pongInt) :: Nil),
        pingString -> ((0.9, pongInt) :: (0.1, exitPoint) :: Nil),
        pongInt -> ((0.9, pingString) :: (0.1, exitPoint) :: Nil)
      )
    )
  }

}
