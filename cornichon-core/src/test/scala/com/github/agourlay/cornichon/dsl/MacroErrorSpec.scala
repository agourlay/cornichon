package com.github.agourlay.cornichon.dsl

import org.scalatest.{ WordSpec, Matchers }

class MacroErrorSpec extends WordSpec with Matchers {

  "Macro" should {
    "compile valid feature definitions" in {
      """
        import com.github.agourlay.cornichon.feature.BaseFeature
        import com.github.agourlay.cornichon.steps.regular.EffectStep
        import scala.concurrent.Future

        class Foo extends BaseFeature {
          val feature =
            Feature("foo") {
              Scenario("aaa") {
                EffectStep.fromSync("just testing", identity)

                Repeat(10) {
                  EffectStep.fromAsync("just testing repeat", s => Future.successful(s))
                }
              }
            }
        }
      """ should compile
    }

    "not compile if feature definition contains invalid expressions" in {
      """
        import com.github.agourlay.cornichon.feature.BaseFeature
        import com.github.agourlay.cornichon.steps.regular.EffectStep

        class Foo extends BaseFeature {
          val feature =
            Feature("foo") {
              Scenario("aaa") {
                EffectStep.fromAsync("just testing", s => Future.successful(s))

                val oops = "Hello World!"

                Repeat(10) {
                  EffectStep.fromSync("just testing repeat", identity)
                }
              }
            }
        }
      """ shouldNot typeCheck
    }
  }

}
