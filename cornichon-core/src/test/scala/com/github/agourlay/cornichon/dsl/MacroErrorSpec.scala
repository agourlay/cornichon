package com.github.agourlay.cornichon.dsl

import utest._

object MacroErrorSpec extends TestSuite {

  val tests = Tests {
    test("macro not compile feature definitions containing imports in DSL") {
      compileError {
        """
        import com.github.agourlay.cornichon.dsl.BaseFeature
        import com.github.agourlay.cornichon.dsl.CoreDsl
        import com.github.agourlay.cornichon.steps.regular.EffectStep

        class Foo extends BaseFeature with CoreDsl {
          val feature =
            Feature("foo") {
              import scala.concurrent.Future
              Scenario("aaa") {
                EffectStep.fromSync("just testing", _.session)

                Repeat(10) {
                  EffectStep.fromAsync("just testing repeat", sc => Future.successful(sc.session))
                }
              }
            }
        }
      """
      }
    }

    test("macro not compile if feature definition contains invalid expressions") {
      compileError {
        """
        import com.github.agourlay.cornichon.dsl.BaseFeature
        import com.github.agourlay.cornichon.dsl.CoreDsl
        import com.github.agourlay.cornichon.steps.regular.EffectStep

        class Foo extends BaseFeature with CoreDsl {
          val feature =
            Feature("foo") {
              Scenario("aaa") {
                EffectStep.fromAsync("just testing", sc => Future.successful(sc.session))

                val oops = "Hello World!"

                Repeat(10) {
                  EffectStep.fromSync("just testing repeat", _.session)
                }
              }
            }
        }
      """
      }
    }
  }
}
