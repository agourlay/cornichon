package com.github.agourlay.cornichon.json

import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.json.JsonSteps.JsonStepBuilder
import com.github.agourlay.cornichon.steps.StepUtilSpec
import utest._

object JsonStepsSpec extends TestSuite with StepUtilSpec {

  private val testKey = "test-key"
  private val jsonStepBuilder = JsonStepBuilder(SessionKey(testKey), Some("test body"))

  val tests = Tests {
    test("JsonStepBuilder.is malformed Json fail") {
      val session = Session.newEmpty.addValuesUnsafe(testKey -> "test")
      val step = jsonStepBuilder.is("{test:")
      val s = Scenario("scenario with JsonSteps", step :: Nil)
      val res = awaitTask(ScenarioRunner.runScenario(session)(s))
      assert(!res.isSuccess)
    }

    test("JsonStepBuilder.path.is json") {
      val session = Session.newEmpty.addValuesUnsafe(testKey -> """{ "myKey" : "myValue" }""")
      val step = jsonStepBuilder.path("myKey").is("myValue")
      val s = Scenario("scenario with JsonSteps", step :: Nil)
      val res = awaitTask(ScenarioRunner.runScenario(session)(s))
      assert(res.isSuccess)
    }

    test("JsonStepBuilder.isPresent path to json key") {
      val session = Session.newEmpty.addValuesUnsafe(testKey -> """{ "myKey" : "myValue" }""")
      val step = jsonStepBuilder.path("myKey").isPresent
      val s = Scenario("scenario with JsonSteps", step :: Nil)
      val res = awaitTask(ScenarioRunner.runScenario(session)(s))
      assert(res.isSuccess)
    }

    test("JsonStepBuilder.isPresent path to json key fail") {
      val session = Session.newEmpty.addValuesUnsafe(testKey -> """{ "myKey" : "myValue" }""")
      val step = jsonStepBuilder.path("myKey2").isPresent
      val s = Scenario("scenario with JsonSteps", step :: Nil)
      val res = awaitTask(ScenarioRunner.runScenario(session)(s))
      assert(!res.isSuccess)
    }

    test("JsonStepBuilder.isAbsent path to json key") {
      val session = Session.newEmpty.addValuesUnsafe(testKey -> """{ "myKey" : "myValue" }""")
      val step = jsonStepBuilder.path("myKey2").isAbsent
      val s = Scenario("scenario with JsonSteps", step :: Nil)
      val res = awaitTask(ScenarioRunner.runScenario(session)(s))
      assert(res.isSuccess)
    }

    test("JsonStepBuilder.isAbsent path to json key fail") {
      val session = Session.newEmpty.addValuesUnsafe(testKey -> """{ "myKey" : "myValue" }""")
      val step = jsonStepBuilder.path("myKey").isAbsent
      val s = Scenario("scenario with JsonSteps", step :: Nil)
      val res = awaitTask(ScenarioRunner.runScenario(session)(s))
      assert(!res.isSuccess)
    }

    test("JsonStepBuilder.isNull path to json key") {
      val session = Session.newEmpty.addValuesUnsafe(testKey -> """{ "myKey" : null }""")
      val step = jsonStepBuilder.path("myKey").isNull
      val s = Scenario("scenario with JsonSteps", step :: Nil)
      val res = awaitTask(ScenarioRunner.runScenario(session)(s))
      assert(res.isSuccess)
    }

    test("JsonStepBuilder.isNull path to json key fail") {
      val session = Session.newEmpty.addValuesUnsafe(testKey -> """{ "myKey" : "notNull" }""")
      val step = jsonStepBuilder.path("myKey").isNull
      val s = Scenario("scenario with JsonSteps", step :: Nil)
      val res = awaitTask(ScenarioRunner.runScenario(session)(s))
      assert(!res.isSuccess)
    }

    test("JsonStepBuilder.isNotNull path to json key") {
      val session = Session.newEmpty.addValuesUnsafe(testKey -> """{ "myKey" : "null" }""")
      val step = jsonStepBuilder.path("myKey").isNotNull
      val s = Scenario("scenario with JsonSteps", step :: Nil)
      val res = awaitTask(ScenarioRunner.runScenario(session)(s))
      assert(res.isSuccess)
    }

    test("JsonStepBuilder.isNotNull path to json key fail") {
      val session = Session.newEmpty.addValuesUnsafe(testKey -> """{ "myKey" : null }""")
      val step = jsonStepBuilder.path("myKey").isNotNull
      val s = Scenario("scenario with JsonSteps", step :: Nil)
      val res = awaitTask(ScenarioRunner.runScenario(session)(s))
      assert(!res.isSuccess)
    }

    test("JsonStepBuilder.is with absent path to json key") {
      val session = Session.newEmpty.addValuesUnsafe(testKey -> """{ "myKey" : "myValue" }""")
      val step = jsonStepBuilder.path("myKey1").is("myValue")
      val s = Scenario("scenario with JsonSteps", step :: Nil)
      val res = awaitTask(ScenarioRunner.runScenario(session)(s))
      assert(!res.isSuccess)
    }

    test("JsonStepBuilder.is json with ignore") {
      val session = Session.newEmpty.addValuesUnsafe(testKey -> """{ "myKey" : "myValue", "myKeyOther" : "myOtherValue" }""")
      val step = jsonStepBuilder.ignoring("myKey").is("""{ "myKeyOther" : "myOtherValue" }""")
      val s = Scenario("scenario with JsonSteps", step :: Nil)
      val res = awaitTask(ScenarioRunner.runScenario(session)(s))
      assert(res.isSuccess)
    }

    test("JsonStepBuilder.is json with ignore provided in expected anyway") {
      val session = Session.newEmpty.addValuesUnsafe(testKey -> """{ "myKey" : "myValue", "myKeyOther" : "myOtherValue" }""")
      val step = jsonStepBuilder.ignoring("myKey").is("""{ "myKey" : "myValue", "myKeyOther" : "myOtherValue" }""")
      val s = Scenario("scenario with JsonSteps", step :: Nil)
      val res = awaitTask(ScenarioRunner.runScenario(session)(s))
      assert(res.isSuccess)
    }

    test("JsonStepBuilder.is json ignoring absent key does not fail") {
      val session = Session.newEmpty.addValuesUnsafe(testKey -> """{ "myKey" : "myValue", "myKeyOther" : "myOtherValue" }""")
      val step = jsonStepBuilder.ignoring("myKey", "myKey1").is("""{ "myKeyOther" : "myOtherValue" }""")
      val s = Scenario("scenario with JsonSteps", step :: Nil)
      val res = awaitTask(ScenarioRunner.runScenario(session)(s))
      assert(res.isSuccess)
    }

    test("JsonStepBuilder.is json with whitelisting") {
      val session = Session.newEmpty.addValuesUnsafe(testKey -> """{ "myKey" : "myValue", "myKeyOther" : "myOtherValue" }""")
      val step = jsonStepBuilder.whitelisting.is("""{ "myKeyOther" : "myOtherValue" }""")
      val s = Scenario("scenario with JsonSteps", step :: Nil)
      val res = awaitTask(ScenarioRunner.runScenario(session)(s))
      assert(res.isSuccess)
    }

    test("JsonStepBuilder.is json without whitelisting enabled") {
      val session = Session.newEmpty.addValuesUnsafe(testKey -> """{ "myKey" : "myValue", "myKeyOther" : "myOtherValue" }""")
      // forcing whitelisting to false to have the negative test
      val step = jsonStepBuilder.copy(whitelist = false).is("""{ "myKeyOther" : "myOtherValue" }""")
      val s = Scenario("scenario with JsonSteps", step :: Nil)
      val res = awaitTask(ScenarioRunner.runScenario(session)(s))
      assert(!res.isSuccess)
    }

    test("JsonStepBuilder.is json with ignoring fields and whitelisting - combination not supported") {
      val session = Session.newEmpty.addValuesUnsafe(testKey -> """{ "myKey" : "myValue", "myKeyOther" : "myOtherValue" }""")
      val step = jsonStepBuilder.whitelisting.ignoring("blah").is("""{ "myKeyOther" : "myOtherValue" }""")
      val s = Scenario("scenario with JsonSteps", step :: Nil)
      val res = awaitTask(ScenarioRunner.runScenario(session)(s))
      res match {
        case f: FailureScenarioReport ⇒
          assert(f.failedSteps.head.errors.head.renderedMessage == "usage of 'ignoring' and 'whiteListing' is mutually exclusive")
        case _ ⇒
          assertMatch(res) { case FailureScenarioReport ⇒ }
      }
    }

    test("JsonStepBuilder.is json with matcher in string") {
      val session = Session.newEmpty.addValuesUnsafe(testKey -> "myOtherValue")
      val step = jsonStepBuilder.is("*any-string*")
      val s = Scenario("scenario with JsonSteps", step :: Nil)
      val res = awaitTask(ScenarioRunner.runScenario(session)(s))
      assert(res.isSuccess)
    }

    test("JsonStepBuilder.is json with matcher in string - non trimmed") {
      val session = Session.newEmpty.addValuesUnsafe(testKey -> "myOtherValue")
      val step = jsonStepBuilder.is(" *any-string* ")
      val s = Scenario("scenario with JsonSteps", step :: Nil)
      val res = awaitTask(ScenarioRunner.runScenario(session)(s))
      assert(!res.isSuccess)
    }

    test("JsonStepBuilder.is json with matcher in object") {
      val session = Session.newEmpty.addValuesUnsafe(testKey -> """{ "myKey" : 1, "myKeyOther" : "myOtherValue" }""")
      val step = jsonStepBuilder.is("""{ "myKey" : *any-integer*, "myKeyOther" : *any-string* }""")
      val s = Scenario("scenario with JsonSteps", step :: Nil)
      val res = awaitTask(ScenarioRunner.runScenario(session)(s))
      assert(res.isSuccess)
    }

    test("JsonStepBuilder.is json with matchers in array") {
      val session = Session.newEmpty.addValuesUnsafe(testKey -> """[{ "myKey" : 1, "myKeyOther" : "myOtherValue" }]""")
      val step = jsonStepBuilder.is("""[{ "myKey" : *any-integer*, "myKeyOther" : *any-string* }]""")
      val s = Scenario("scenario with JsonSteps", step :: Nil)
      val res = awaitTask(ScenarioRunner.runScenario(session)(s))
      assert(res.isSuccess)
    }

    test("JsonStepBuilder.is case class asJson with placeholder") {
      import io.circe.generic.auto._
      import io.circe.syntax._
      case class MyObj(myKey: String, myKeyOther: String)

      val jsonString = """{ "myKey" : "myValue", "myKeyOther" : "myOtherValue" }"""
      val session = Session.newEmpty.addValuesUnsafe(testKey -> jsonString, "a-placeholder" -> "myOtherValue")

      val instance = MyObj("myValue", "<a-placeholder>")

      val step = jsonStepBuilder.is(instance.asJson)
      val s = Scenario("scenario with JsonSteps", step :: Nil)
      val res = awaitTask(ScenarioRunner.runScenario(session)(s))
      assert(res.isSuccess)
    }

    test("JsonArrayStepBuilder.is not an array fail") {
      val session = Session.newEmpty.addValuesUnsafe(testKey -> "test")
      val step = jsonStepBuilder.asArray.is("test")
      val s = Scenario("scenario with JsonArraySteps", step :: Nil)
      val res = awaitTask(ScenarioRunner.runScenario(session)(s))
      assert(!res.isSuccess)
    }

    test("JsonArrayStepBuilder.is array fail") {
      val session = Session.newEmpty.addValuesUnsafe(testKey -> """["a", "b", "c" ]""")
      val step = jsonStepBuilder.asArray.is("""["a", "b". "c" ]""")
      val s = Scenario("scenario with JsonArraySteps", step :: Nil)
      val res = awaitTask(ScenarioRunner.runScenario(session)(s))
      assert(!res.isSuccess)
    }

    test("JsonArrayStepBuilder.has array size") {
      val session = Session.newEmpty.addValuesUnsafe(testKey -> """["a", "b", "c" ]""")
      val step = jsonStepBuilder.asArray.hasSize(3)
      val s = Scenario("scenario with JsonArraySteps", step :: Nil)
      val res = awaitTask(ScenarioRunner.runScenario(session)(s))
      assert(res.isSuccess)
    }

    // goes through the new experimental GenericAssertStepBuilder
    test("JsonArrayStepBuilder array size is") {
      val session = Session.newEmpty.addValuesUnsafe(testKey -> """["a", "b", "c" ]""")
      val step = jsonStepBuilder.asArray.size.is(3)
      val s = Scenario("scenario with JsonArraySteps", step :: Nil)
      val res = awaitTask(ScenarioRunner.runScenario(session)(s))
      assert(res.isSuccess)
    }

    test("JsonArrayStepBuilder array size is greater") {
      val session = Session.newEmpty.addValuesUnsafe(testKey -> """["a", "b", "c" ]""")
      val step = jsonStepBuilder.asArray.size.isGreaterThan(2)
      val s = Scenario("scenario with JsonArraySteps", step :: Nil)
      val res = awaitTask(ScenarioRunner.runScenario(session)(s))
      assert(res.isSuccess)
    }

    test("JsonArrayStepBuilder array size is less") {
      val session = Session.newEmpty.addValuesUnsafe(testKey -> """["a", "b", "c" ]""")
      val step = jsonStepBuilder.asArray.size.isLessThan(4)
      val s = Scenario("scenario with JsonArraySteps", step :: Nil)
      val res = awaitTask(ScenarioRunner.runScenario(session)(s))
      assert(res.isSuccess)
    }

    test("JsonArrayStepBuilder array size is between") {
      val session = Session.newEmpty.addValuesUnsafe(testKey -> """["a", "b", "c" ]""")
      val step = jsonStepBuilder.asArray.size.isBetween(2, 4)
      val s = Scenario("scenario with JsonArraySteps", step :: Nil)
      val res = awaitTask(ScenarioRunner.runScenario(session)(s))
      assert(res.isSuccess)
    }

    test("JsonArrayStepBuilder not empty array") {
      val session = Session.newEmpty.addValuesUnsafe(testKey -> """["a", "b", "c" ]""")
      val step = jsonStepBuilder.asArray.isNotEmpty
      val s = Scenario("scenario with JsonArraySteps", step :: Nil)
      val res = awaitTask(ScenarioRunner.runScenario(session)(s))
      assert(res.isSuccess)
    }

    test("JsonArrayStepBuilder not empty array fail") {
      val session = Session.newEmpty.addValuesUnsafe(testKey -> """[]""")
      val step = jsonStepBuilder.asArray.isNotEmpty
      val s = Scenario("scenario with JsonArraySteps", step :: Nil)
      val res = awaitTask(ScenarioRunner.runScenario(session)(s))
      assert(!res.isSuccess)
    }

    test("JsonArrayStepBuilder is empty array") {
      val session = Session.newEmpty.addValuesUnsafe(testKey -> """[]""")
      val step = jsonStepBuilder.asArray.isEmpty
      val s = Scenario("scenario with JsonArraySteps", step :: Nil)
      val res = awaitTask(ScenarioRunner.runScenario(session)(s))
      assert(res.isSuccess)
    }

    test("JsonArrayStepBuilder is empty array fail") {
      val session = Session.newEmpty.addValuesUnsafe(testKey -> """["a", "b", "c" ]""")
      val step = jsonStepBuilder.asArray.isEmpty
      val s = Scenario("scenario with JsonArraySteps", step :: Nil)
      val res = awaitTask(ScenarioRunner.runScenario(session)(s))
      assert(!res.isSuccess)
    }

    test("JsonArrayStepBuilder has array size fail") {
      val session = Session.newEmpty.addValuesUnsafe(testKey -> """["a", "b", "c" ]""")
      val step = jsonStepBuilder.asArray.hasSize(2)
      val s = Scenario("scenario with JsonArraySteps", step :: Nil)
      val res = awaitTask(ScenarioRunner.runScenario(session)(s))
      assert(!res.isSuccess)
    }

    test("JsonArrayStepBuilder is not ordered by default") {
      val session = Session.newEmpty.addValuesUnsafe(testKey -> """["a", "b", "c" ]""")
      val step = jsonStepBuilder.asArray.is("""["a", "c", "b" ]""")
      val s = Scenario("scenario with JsonArraySteps", step :: Nil)
      val res = awaitTask(ScenarioRunner.runScenario(session)(s))
      assert(res.isSuccess)
    }

    test("JsonArrayStepBuilder in order is") {
      val session = Session.newEmpty.addValuesUnsafe(testKey -> """["a", "b", "c" ]""")
      val step = jsonStepBuilder.asArray.inOrder.is("""["a", "c", "b" ]""")
      val s = Scenario("scenario with JsonArraySteps", step :: Nil)
      val res = awaitTask(ScenarioRunner.runScenario(session)(s))
      assert(!res.isSuccess)
    }

    test("JsonArrayStepBuilder.contains") {
      val session = Session.newEmpty.addValuesUnsafe(testKey -> """["a", "b", "c" ]""")
      val step = jsonStepBuilder.asArray.contains("b", "c")
      val s = Scenario("scenario with JsonArraySteps", step :: Nil)
      val res = awaitTask(ScenarioRunner.runScenario(session)(s))
      assert(res.isSuccess)
    }

    test("JsonArrayStepBuilder.contains fail") {
      val session = Session.newEmpty.addValuesUnsafe(testKey -> """["a", "b", "c" ]""")
      val step = jsonStepBuilder.asArray.contains("d")
      val s = Scenario("scenario with JsonArraySteps", step :: Nil)
      val res = awaitTask(ScenarioRunner.runScenario(session)(s))
      assert(!res.isSuccess)
    }

    test("JsonArrayStepBuilder.contains fail - partial") {
      val session = Session.newEmpty.addValuesUnsafe(testKey -> """["a", "b", "c" ]""")
      val step = jsonStepBuilder.asArray.contains("c", "d")
      val s = Scenario("scenario with JsonArraySteps", step :: Nil)
      val res = awaitTask(ScenarioRunner.runScenario(session)(s))
      assert(!res.isSuccess)
    }

    test("JsonArrayStepBuilder.not contains fail") {
      val session = Session.newEmpty.addValuesUnsafe(testKey -> """["a", "b", "c" ]""")
      val step = jsonStepBuilder.asArray.not_contains("c")
      val s = Scenario("scenario with JsonArraySteps", step :: Nil)
      val res = awaitTask(ScenarioRunner.runScenario(session)(s))
      assert(!res.isSuccess)
    }

    test("JsonArrayStepBuilder.not contains") {
      val session = Session.newEmpty.addValuesUnsafe(testKey -> """["a", "b", "c" ]""")
      val step = jsonStepBuilder.asArray.not_contains("d")
      val s = Scenario("scenario with JsonArraySteps", step :: Nil)
      val res = awaitTask(ScenarioRunner.runScenario(session)(s))
      assert(res.isSuccess)
    }

    test("JsonArrayStepBuilder.not contains - partial") {
      val session = Session.newEmpty.addValuesUnsafe(testKey -> """["a", "b", "c" ]""")
      val step = jsonStepBuilder.asArray.not_contains("c", "d")
      val s = Scenario("scenario with JsonArraySteps", step :: Nil)
      val res = awaitTask(ScenarioRunner.runScenario(session)(s))
      assert(res.isSuccess)
    }

    test("JsonArrayStepBuilder.is json with ignoreEach") {
      val session = Session.newEmpty.addValuesUnsafe(testKey -> """[{ "myKey" : "myValue", "myKeyOther" : "myOtherValue" }]""")
      val step = jsonStepBuilder.asArray.ignoringEach("myKey").is("""[{ "myKeyOther" : "myOtherValue" }]""")
      val s = Scenario("scenario with JsonSteps", step :: Nil)
      val res = awaitTask(ScenarioRunner.runScenario(session)(s))
      assert(res.isSuccess)
    }

    test("JsonArrayStepBuilder.is json with ignoreEach provided in expected anyway") {
      val session = Session.newEmpty.addValuesUnsafe(testKey -> """[{ "myKey" : "myValue", "myKeyOther" : "myOtherValue" }]""")
      val step = jsonStepBuilder.asArray.ignoringEach("myKey").is("""[{ "myKey" : "myValue", "myKeyOther" : "myOtherValue" }]""")
      val s = Scenario("scenario with JsonSteps", step :: Nil)
      val res = awaitTask(ScenarioRunner.runScenario(session)(s))
      assert(res.isSuccess)
    }

    test("JsonArrayStepBuilder.is json with matcher") {
      val session = Session.newEmpty.addValuesUnsafe(testKey -> """[{ "myKey" : "myValue", "myKeyOther" : "myOtherValue" }]""")
      val step = jsonStepBuilder.asArray.ignoringEach("$.*.myKey").is("""{ "myKeyOther" : *any-string* }""")
      val s = Scenario("scenario with JsonSteps", step :: Nil)
      val res = awaitTask(ScenarioRunner.runScenario(session)(s))
      res match {
        case f: FailureScenarioReport ⇒
          assert(f.failedSteps.head.errors.head.renderedMessage ==
            """
              |matchers are not supported in `asArray` assertion but *any-string* found
              |https://github.com/agourlay/cornichon/issues/135"""
            .stripMargin.trim)
        case _ ⇒
          assertMatch(res) { case FailureScenarioReport ⇒ }
      }
    }
  }
}
