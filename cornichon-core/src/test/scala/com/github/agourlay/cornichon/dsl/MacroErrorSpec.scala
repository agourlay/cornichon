package com.github.agourlay.cornichon.dsl

import munit.FunSuite

class MacroErrorSpec extends FunSuite {

  test("macro not compile feature definitions containing imports in DSL") {
    compileErrors {
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
    compileErrors {
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

  test("macro  compiles if the step is reference to identifier `name`") {
    import com.github.agourlay.cornichon.core.Step
    import scala.concurrent.duration._

    class Foo extends BaseFeature with CoreDsl {

      def testMethod(foo: Step): Step =
        Eventually(maxDuration = 15.seconds, interval = 200.milliseconds) {
          foo
        }

      val feature =
        Feature("foo") {
          Scenario("aaa") {
            testMethod(print_step("Hello world!"))
          }
        }
    }
  }
}
