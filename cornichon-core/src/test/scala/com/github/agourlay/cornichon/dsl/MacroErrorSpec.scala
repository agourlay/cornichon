package com.github.agourlay.cornichon.dsl

import com.github.agourlay.cornichon.core.NoOpStep
import com.github.agourlay.cornichon.steps.wrapped.EventuallyStep
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

  test("macro compiles if the step is reference to identifier `name`") {
    import com.github.agourlay.cornichon.core.Step
    import scala.concurrent.duration._

    object Foo extends BaseFeature with CoreDsl {

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

    Foo.feature.scenarios.head.steps.head match {
      case s: EventuallyStep => assert(s.nested.head.title == "print step")
      case other             => fail(s"Expected EventuallyStep but got $other")
    }
  }

  test("macro compiles and includes multiple steps") {

    object Foo extends BaseFeature with CoreDsl {

      val feature =
        Feature("foo") {
          Scenario("aaa") {
            print_step("Hello world!")
            print_step("Hello world!")
            print_step("Hello world!")
          }
        }
    }

    assert(Foo.feature.scenarios.head.steps.size == 3, "size is " + Foo.feature.scenarios.head.steps.size)
  }

  test("macro compiles and includes multiple steps as a seq (literal)") {

    object Foo extends BaseFeature with CoreDsl {

      val feature =
        Feature("foo") {
          Scenario("aaa") {
            Seq(
              print_step("Hello world!"),
              print_step("Hello world!"),
              print_step("Hello world!")
            )
          }
        }
    }

    assert(Foo.feature.scenarios.head.steps.size == 3, "size is " + Foo.feature.scenarios.head.steps.size)
  }

  test("macro compiles and includes multiple steps as a seq (non-literal)") {

    object Foo extends BaseFeature with CoreDsl {

      val steps =
        Seq(
          print_step("Hello world!"),
          print_step("Hello world!"),
          print_step("Hello world!")
        )

      val feature =
        Feature("foo") {
          Scenario("aaa") {
            steps
          }
        }
    }

    assert(Foo.feature.scenarios.head.steps.size == 3, "size is " + Foo.feature.scenarios.head.steps.size)
  }

  test("macro preserves order") {

    object Foo extends BaseFeature with CoreDsl {
      val steps =
        Vector(
          NoOpStep,
          print_step("Hello world!"),
        )

      val feature =
        Feature("foo") {
          Scenario("aaa") {
            print_step("Hello world!")
            steps
            Seq(
              print_step("Hello world!"),
              print_step("Hello world!")
            )
          }
          Scenario("bbb") {
            NoOpStep
            print_step("Hello world!")
          }
        }
    }

    assert(Foo.feature.scenarios.head.steps.map(_.title) == List("print step", "noOp", "print step", "print step", "print step"))
    assert(Foo.feature.scenarios(1).steps.map(_.title) == List("noOp", "print step"))
  }
}
