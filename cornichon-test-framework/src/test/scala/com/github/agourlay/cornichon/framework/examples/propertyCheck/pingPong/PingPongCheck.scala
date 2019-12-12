package com.github.agourlay.cornichon.framework.examples.propertyCheck.pingPong

import com.github.agourlay.cornichon.CornichonFeature
import com.github.agourlay.cornichon.steps.check.checkModel.{ Model, ModelRunner, Property2 }
import com.github.agourlay.cornichon.core.{ NoOpStep, RandomContext, ValueGenerator }
import com.github.agourlay.cornichon.steps.regular.assertStep.{ AssertStep, GenericEqualityAssertion }

class PingPongCheck extends CornichonFeature {

  def feature = Feature("Basic examples of checks") {

    Scenario("ping pong check") {

      Given I check_model(maxNumberOfRuns = 2, maxNumberOfTransitions = 10)(myModelRunner)

    }
  }

  def stringGen(rc: RandomContext): ValueGenerator[String] = ValueGenerator(
    name = "an alphanumeric String",
    gen = () => rc.alphanumeric.take(20).mkString(""))

  def assert_String_20(s: String) = AssertStep(
    title = s"String has length 20 '$s'",
    action = _ => GenericEqualityAssertion(20, s.length)
  )

  def integerGen(rc: RandomContext): ValueGenerator[Int] = ValueGenerator(
    name = "integer",
    gen = () => rc.nextInt(10000))

  def assert_Integer_less_than_10000(i: Int) = AssertStep(
    title = s"Integer less than 10000 '$i'",
    action = _ => GenericEqualityAssertion(true, i < 10000)
  )

  val myModelRunner = ModelRunner.make[String, Int](stringGen, integerGen) {

    val entryPoint = Property2[String, Int](
      description = "Entry point",
      invariant = (_, _) => NoOpStep
    )

    val pingString = Property2[String, Int](
      description = "Ping String",
      invariant = (stringGen, _) => assert_String_20(stringGen())
    )

    val pongInt = Property2[String, Int](
      description = "Pong Int",
      invariant = (_, intGen) => assert_Integer_less_than_10000(intGen())
    )

    val exitPoint = Property2[String, Int](
      description = "Exit point",
      invariant = (_, _) => NoOpStep
    )

    Model(
      description = "ping pong model",
      entryPoint = entryPoint,
      transitions = Map(
        entryPoint -> ((50, pingString) :: (50, pongInt) :: Nil),
        pingString -> ((90, pongInt) :: (10, exitPoint) :: Nil),
        pongInt -> ((90, pingString) :: (10, exitPoint) :: Nil)
      )
    )
  }

}
