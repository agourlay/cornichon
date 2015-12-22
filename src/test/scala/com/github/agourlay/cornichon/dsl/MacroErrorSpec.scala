package com.github.agourlay.cornichon.dsl

import org.scalatest.{WordSpec, Matchers}

class MacroErrorSpec extends WordSpec with Matchers {

  "Macro" should {
    "compile valid feature definitions" in {
      """
        import com.github.agourlay.cornichon.CornichonFeature
        import com.github.agourlay.cornichon.core.{Scenario, RunnableStep}

        class Foo extends CornichonFeature {
          val feature =
            Feature("foo") {
              Scenario("aaa") {
                RunnableStep.effectful("just testing", identity)

                Repeat(10) {
                  RunnableStep.effectful("just testing repeat", identity)
                }
              }
            }
        }
      """ should compile
    }

    "not compile if feature definition contains invalid expressions" in {
      """
        import com.github.agourlay.cornichon.CornichonFeature
        import com.github.agourlay.cornichon.core.{Scenario, RunnableStep}

        class Foo extends CornichonFeature {
          val feature =
            Feature("foo") {
              Scenario("aaa") {
                RunnableStep.effectful("just testing", identity)

                val oops = "Hello World!"

                Repeat(10) {
                  RunnableStep.effectful("just testing repeat", identity)
                }
              }
            }
        }
      """ shouldNot typeCheck
    }
  }

}
