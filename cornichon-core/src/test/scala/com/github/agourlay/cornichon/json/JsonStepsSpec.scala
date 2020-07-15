package com.github.agourlay.cornichon.json

import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.json.JsonSteps.JsonStepBuilder
import com.github.agourlay.cornichon.steps.regular.assertStep.{ GenericEqualityAssertion, GreaterThanAssertion, LessThanAssertion }
import com.github.agourlay.cornichon.testHelpers.CommonTestSuite
import io.circe.Json
import utest._

object JsonStepsSpec extends TestSuite with CommonTestSuite {

  private val testKey = "test-key"
  private val jsonStepBuilder = JsonStepBuilder(SessionKey(testKey), Some("test body"))

  val tests = Tests {
    test("JsonStepBuilder.is malformed Json fail") {
      val session = Session.newEmpty.addValuesUnsafe(testKey -> "test")
      val step = jsonStepBuilder.is("{test:")
      val s = Scenario("scenario with JsonSteps", step :: Nil)
      val res = awaitTask(ScenarioRunner.runScenario(session)(s))
      scenarioFailsWithMessage(res) {
        """|Scenario 'scenario with JsonSteps' failed:
           |
           |at step:
           |test body is
           |{test:
           |
           |with error(s):
           |malformed JSON error
           |expected " got 'test:' (line 1, column 2)
           |for input
           |{test:
           |
           |seed for the run was '1'
           |""".stripMargin
      }
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
      scenarioFailsWithMessage(res) {
        """|Scenario 'scenario with JsonSteps' failed:
           |
           |at step:
           |test body's field 'myKey2' is present
           |
           |with error(s):
           |expected key 'myKey2' to be present but it was not in the source:
           |{
           |  "myKey" : "myValue"
           |}
           |
           |seed for the run was '1'
           |""".stripMargin
      }
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
      scenarioFailsWithMessage(res) {
        """|Scenario 'scenario with JsonSteps' failed:
           |
           |at step:
           |test body's field 'myKey' is absent
           |
           |with error(s):
           |expected key 'myKey' to be absent but it was found with value:
           |"myValue"
           |
           |seed for the run was '1'
           |""".stripMargin
      }
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
      scenarioFailsWithMessage(res) {
        """|Scenario 'scenario with JsonSteps' failed:
           |
           |at step:
           |test body's field 'myKey' is null
           |
           |with error(s):
           |expected result was:
           |'null'
           |but actual result is:
           |'"notNull"'
           |
           |JSON patch between actual result and expected result is :
           |[
           |  {
           |    "op" : "replace",
           |    "path" : "",
           |    "value" : "notNull",
           |    "old" : null
           |  }
           |]
           |
           |seed for the run was '1'
           |""".stripMargin
      }
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
      scenarioFailsWithMessage(res) {
        """|Scenario 'scenario with JsonSteps' failed:
           |
           |at step:
           |test body's field 'myKey' is not null
           |
           |with error(s):
           |expected key 'myKey' to not be null but it was null in the source:
           |{
           |  "myKey" : null
           |}
           |
           |seed for the run was '1'
           |""".stripMargin
      }
    }

    test("JsonStepBuilder.is with absent path to json key") {
      val session = Session.newEmpty.addValuesUnsafe(testKey -> """{ "myKey" : "myValue" }""")
      val step = jsonStepBuilder.path("myKey1").is("myValue")
      val s = Scenario("scenario with JsonSteps", step :: Nil)
      val res = awaitTask(ScenarioRunner.runScenario(session)(s))
      scenarioFailsWithMessage(res) {
        """|Scenario 'scenario with JsonSteps' failed:
           |
           |at step:
           |test body's field 'myKey1' is
           |myValue
           |
           |with error(s):
           |JSON path 'myKey1' is not defined in object
           |{
           |  "myKey" : "myValue"
           |}
           |
           |seed for the run was '1'
           |""".stripMargin
      }
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
      scenarioFailsWithMessage(res) {
        """|Scenario 'scenario with JsonSteps' failed:
           |
           |at step:
           |test body is
           |{ "myKeyOther" : "myOtherValue" }
           |
           |with error(s):
           |expected result was:
           |'{
           |  "myKeyOther" : "myOtherValue"
           |}'
           |but actual result is:
           |'{
           |  "myKey" : "myValue",
           |  "myKeyOther" : "myOtherValue"
           |}'
           |
           |JSON patch between actual result and expected result is :
           |[
           |  {
           |    "op" : "add",
           |    "path" : "/myKey",
           |    "value" : "myValue"
           |  }
           |]
           |
           |seed for the run was '1'
           |""".stripMargin
      }
    }

    test("JsonStepBuilder.is json with ignoring fields and whitelisting - combination not supported") {
      val session = Session.newEmpty.addValuesUnsafe(testKey -> """{ "myKey" : "myValue", "myKeyOther" : "myOtherValue" }""")
      val step = jsonStepBuilder.whitelisting.ignoring("blah").is("""{ "myKeyOther" : "myOtherValue" }""")
      val s = Scenario("scenario with JsonSteps", step :: Nil)
      val res = awaitTask(ScenarioRunner.runScenario(session)(s))
      scenarioFailsWithMessage(res) {
        """|Scenario 'scenario with JsonSteps' failed:
           |
           |at step:
           |test body is
           |{ "myKeyOther" : "myOtherValue" } with white listing ignoring keys blah
           |
           |with error(s):
           |usage of 'ignoring' and 'whiteListing' is mutually exclusive
           |
           |seed for the run was '1'
           |""".stripMargin
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
      val step = jsonStepBuilder.is(" *any-string*")
      val s = Scenario("scenario with JsonSteps", step :: Nil)
      val res = awaitTask(ScenarioRunner.runScenario(session)(s))
      scenarioFailsWithMessage(res) {
        """|Scenario 'scenario with JsonSteps' failed:
           |
           |at step:
           |test body is
           | *any-string*
           |
           |with error(s):
           |expected result was:
           |'" *any-string*"'
           |but actual result is:
           |'"myOtherValue"'
           |
           |JSON patch between actual result and expected result is :
           |[
           |  {
           |    "op" : "replace",
           |    "path" : "",
           |    "value" : "myOtherValue",
           |    "old" : " *any-string*"
           |  }
           |]
           |
           |seed for the run was '1'
           |""".stripMargin
      }
    }

    test("JsonStepBuilder.is json with matcher in object") {
      val session = Session.newEmpty.addValuesUnsafe(testKey -> """{ "myKey" : 1, "myKeyOther" : "myOtherValue" }""")
      val step = jsonStepBuilder.is("""{ "myKey" : *any-integer*, "myKeyOther" : *any-string* }""")
      val s = Scenario("scenario with JsonSteps", step :: Nil)
      val res = awaitTask(ScenarioRunner.runScenario(session)(s))
      assert(res.isSuccess)
    }

    test("JsonStepBuilder.is json with matcher absent in actual object") {
      val session = Session.newEmpty.addValuesUnsafe(testKey -> """{ "myKey" : 1 }""")
      val step = jsonStepBuilder.is("""{ "myKey" : *any-integer*, "myKeyOther" : *any-string* }""")
      val s = Scenario("scenario with JsonSteps", step :: Nil)
      val res = awaitTask(ScenarioRunner.runScenario(session)(s))
      scenarioFailsWithMessage(res) {
        """|Scenario 'scenario with JsonSteps' failed:
           |
           |at step:
           |test body is
           |{ "myKey" : *any-integer*, "myKeyOther" : *any-string* }
           |
           |with error(s):
           |evaluation of matcher 'any-string' (checks if the field is a String) failed for input '{
           |  "myKey" : 1
           |}'
           |caused by:
           |JSON path '$.myKeyOther' is not defined in object
           |{
           |  "myKey" : 1
           |}
           |
           |seed for the run was '1'
           |""".stripMargin
      }
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

    test("JsonStepBuilder.compareWithPreviousValue Json") {
      val session = Session.newEmpty
        .addValuesUnsafe(testKey -> """{ "myKey" : "myValue", "myKeyOther" : "myOtherValue" }""")
        .addValuesUnsafe(testKey -> """{ "myKey" : "myValue", "myKeyOther" : "myOtherValue" }""")
      val step = jsonStepBuilder.compareWithPreviousValue[Json] { case (prev, current) => GenericEqualityAssertion(prev, current) }
      val s = Scenario("scenario with JsonSteps", step :: Nil)
      val res = awaitTask(ScenarioRunner.runScenario(session)(s))
      assert(res.isSuccess)
    }

    test("JsonStepBuilder.compareWithPreviousValue ignore field Json") {
      val session = Session.newEmpty
        .addValuesUnsafe(testKey -> """{ "myKey" : "myValue", "myKeyOther" : "myOtherValue" }""")
        .addValuesUnsafe(testKey -> """{ "myKey" : "myValue", "myKeyOther" : "myNewValue" }""")
      val step = jsonStepBuilder.ignoring("myKeyOther").compareWithPreviousValue[Json] { case (prev, current) => GenericEqualityAssertion(prev, current) }
      val s = Scenario("scenario with JsonSteps", step :: Nil)
      val res = awaitTask(ScenarioRunner.runScenario(session)(s))
      assert(res.isSuccess)
    }

    test("JsonStepBuilder.compareWithPreviousValue path to String") {
      val session = Session.newEmpty
        .addValuesUnsafe(testKey -> """{ "myKey" : "myValue", "myKeyOther" : "myOtherValue" }""")
        .addValuesUnsafe(testKey -> """{ "myKey" : "myValue", "myKeyOther" : "myNewValue" }""")
      val step = jsonStepBuilder.path("myKeyOther").compareWithPreviousValue[String] { case (prev, current) => GenericEqualityAssertion(prev, current, negate = true) }
      val s = Scenario("scenario with JsonSteps", step :: Nil)
      val res = awaitTask(ScenarioRunner.runScenario(session)(s))
      assert(res.isSuccess)
    }

    test("JsonStepBuilder.compareWithPreviousValue path String fail") {
      val session = Session.newEmpty
        .addValuesUnsafe(testKey -> """{ "myKey" : "myValue", "myKeyOther" : "myOtherValue" }""")
        .addValuesUnsafe(testKey -> """{ "myKey" : "myValue", "myKeyOther" : "myNewValue" }""")
      val step = jsonStepBuilder.path("myKeyOther").compareWithPreviousValue[String] { case (prev, current) => GenericEqualityAssertion(prev, current) }
      val s = Scenario("scenario with JsonSteps", step :: Nil)
      val res = awaitTask(ScenarioRunner.runScenario(session)(s))
      scenarioFailsWithMessage(res) {
        """|Scenario 'scenario with JsonSteps' failed:
           |
           |at step:
           |compare previous & current value of test body's field 'myKeyOther'
           |
           |with error(s):
           |expected result was:
           |'myOtherValue'
           |but actual result is:
           |'myNewValue'
           |
           |seed for the run was '1'
           |""".stripMargin
      }
    }

    test("JsonStepBuilder.compareWithPreviousValue path to Int") {
      val session = Session.newEmpty
        .addValuesUnsafe(testKey -> """{ "myKey" : "myValue", "myKeyOther" : 1 }""")
        .addValuesUnsafe(testKey -> """{ "myKey" : "myValue", "myKeyOther" : 2 }""")
      val step = jsonStepBuilder.path("myKeyOther").compareWithPreviousValue[Int] { case (prev, current) => LessThanAssertion(prev, current) }
      val s = Scenario("scenario with JsonSteps", step :: Nil)
      val res = awaitTask(ScenarioRunner.runScenario(session)(s))
      assert(res.isSuccess)
    }

    test("JsonStepBuilder.compareWithPreviousValue path to Int fail") {
      val session = Session.newEmpty
        .addValuesUnsafe(testKey -> """{ "myKey" : "myValue", "myKeyOther" : 1 }""")
        .addValuesUnsafe(testKey -> """{ "myKey" : "myValue", "myKeyOther" : 2 }""")
      val step = jsonStepBuilder.path("myKeyOther").compareWithPreviousValue[Int] { case (prev, current) => GreaterThanAssertion(prev, current) }
      val s = Scenario("scenario with JsonSteps", step :: Nil)
      val res = awaitTask(ScenarioRunner.runScenario(session)(s))
      scenarioFailsWithMessage(res) {
        """|Scenario 'scenario with JsonSteps' failed:
           |
           |at step:
           |compare previous & current value of test body's field 'myKeyOther'
           |
           |with error(s):
           |expected '1' to be greater than '2'
           |
           |seed for the run was '1'
           |""".stripMargin
      }
    }

    test("JsonStepBuilder.compareWithPreviousValue path to Boolean") {
      val session = Session.newEmpty
        .addValuesUnsafe(testKey -> """{ "myKey" : "myValue", "myKeyOther" : true }""")
        .addValuesUnsafe(testKey -> """{ "myKey" : "myValue", "myKeyOther" : true }""")
      val step = jsonStepBuilder.path("myKeyOther").compareWithPreviousValue[Boolean] { case (prev, current) => GenericEqualityAssertion(prev, current) }
      val s = Scenario("scenario with JsonSteps", step :: Nil)
      val res = awaitTask(ScenarioRunner.runScenario(session)(s))
      assert(res.isSuccess)
    }

    test("JsonStepBuilder.compareWithPreviousValue path to Boolean fail") {
      val session = Session.newEmpty
        .addValuesUnsafe(testKey -> """{ "myKey" : "myValue", "myKeyOther" : true }""")
        .addValuesUnsafe(testKey -> """{ "myKey" : "myValue", "myKeyOther" : false }""")
      val step = jsonStepBuilder.path("myKeyOther").compareWithPreviousValue[Boolean] { case (prev, current) => GenericEqualityAssertion(prev, current) }
      val s = Scenario("scenario with JsonSteps", step :: Nil)
      val res = awaitTask(ScenarioRunner.runScenario(session)(s))
      scenarioFailsWithMessage(res) {
        """|Scenario 'scenario with JsonSteps' failed:
           |
           |at step:
           |compare previous & current value of test body's field 'myKeyOther'
           |
           |with error(s):
           |expected result was:
           |'true'
           |but actual result is:
           |'false'
           |
           |seed for the run was '1'
           |""".stripMargin
      }
    }

    test("JsonArrayStepBuilder.is not an array fail") {
      val session = Session.newEmpty.addValuesUnsafe(testKey -> "test")
      val step = jsonStepBuilder.asArray.is("test")
      val s = Scenario("scenario with JsonArraySteps", step :: Nil)
      val res = awaitTask(ScenarioRunner.runScenario(session)(s))
      scenarioFailsWithMessage(res) {
        """|Scenario 'scenario with JsonArraySteps' failed:
           |
           |at step:
           |test body array is
           |test
           |
           |with error(s):
           |expected JSON Array but got
           |test
           |
           |seed for the run was '1'
           |""".stripMargin
      }
    }

    test("JsonArrayStepBuilder.is array fail") {
      val session = Session.newEmpty.addValuesUnsafe(testKey -> """[ "a", "b", "c" ]""")
      val step = jsonStepBuilder.asArray.is("""["a", "b". "c" ]""")
      val s = Scenario("scenario with JsonArraySteps", step :: Nil)
      val res = awaitTask(ScenarioRunner.runScenario(session)(s))
      scenarioFailsWithMessage(res) {
        """|Scenario 'scenario with JsonArraySteps' failed:
           |
           |at step:
           |test body array is
           |["a", "b". "c" ]
           |
           |with error(s):
           |malformed JSON error
           |expected ] or , got '. "c" ...' (line 1, column 10)
           |for input
           |["a", "b". "c" ]
           |
           |seed for the run was '1'
           |""".stripMargin
      }
    }

    test("JsonArrayStepBuilder.has array size") {
      val session = Session.newEmpty.addValuesUnsafe(testKey -> """[ "a", "b", "c" ]""")
      val step = jsonStepBuilder.asArray.hasSize(3)
      val s = Scenario("scenario with JsonArraySteps", step :: Nil)
      val res = awaitTask(ScenarioRunner.runScenario(session)(s))
      assert(res.isSuccess)
    }

    // goes through the new experimental GenericAssertStepBuilder
    test("JsonArrayStepBuilder array size is") {
      val session = Session.newEmpty.addValuesUnsafe(testKey -> """[ "a", "b", "c" ]""")
      val step = jsonStepBuilder.asArray.size.is(3)
      val s = Scenario("scenario with JsonArraySteps", step :: Nil)
      val res = awaitTask(ScenarioRunner.runScenario(session)(s))
      assert(res.isSuccess)
    }

    test("JsonArrayStepBuilder array size is greater") {
      val session = Session.newEmpty.addValuesUnsafe(testKey -> """[ "a", "b", "c" ]""")
      val step = jsonStepBuilder.asArray.size.isGreaterThan(2)
      val s = Scenario("scenario with JsonArraySteps", step :: Nil)
      val res = awaitTask(ScenarioRunner.runScenario(session)(s))
      assert(res.isSuccess)
    }

    test("JsonArrayStepBuilder array size is less") {
      val session = Session.newEmpty.addValuesUnsafe(testKey -> """[ "a", "b", "c" ]""")
      val step = jsonStepBuilder.asArray.size.isLessThan(4)
      val s = Scenario("scenario with JsonArraySteps", step :: Nil)
      val res = awaitTask(ScenarioRunner.runScenario(session)(s))
      assert(res.isSuccess)
    }

    test("JsonArrayStepBuilder array size is between") {
      val session = Session.newEmpty.addValuesUnsafe(testKey -> """[ "a", "b", "c" ]""")
      val step = jsonStepBuilder.asArray.size.isBetween(2, 4)
      val s = Scenario("scenario with JsonArraySteps", step :: Nil)
      val res = awaitTask(ScenarioRunner.runScenario(session)(s))
      assert(res.isSuccess)
    }

    test("JsonArrayStepBuilder not empty array") {
      val session = Session.newEmpty.addValuesUnsafe(testKey -> """[ "a", "b", "c" ]""")
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
      scenarioFailsWithMessage(res) {
        """|Scenario 'scenario with JsonArraySteps' failed:
           |
           |at step:
           |test body array size is not empty
           |
           |with error(s):
           |expected JSON array to not be empty but it is not the case in the context of:
           |[
           |]
           |
           |seed for the run was '1'
           |""".stripMargin
      }
    }

    test("JsonArrayStepBuilder is empty array") {
      val session = Session.newEmpty.addValuesUnsafe(testKey -> """[]""")
      val step = jsonStepBuilder.asArray.isEmpty
      val s = Scenario("scenario with JsonArraySteps", step :: Nil)
      val res = awaitTask(ScenarioRunner.runScenario(session)(s))
      assert(res.isSuccess)
    }

    test("JsonArrayStepBuilder is empty array fail") {
      val session = Session.newEmpty.addValuesUnsafe(testKey -> """[ "a", "b", "c" ]""")
      val step = jsonStepBuilder.asArray.isEmpty
      val s = Scenario("scenario with JsonArraySteps", step :: Nil)
      val res = awaitTask(ScenarioRunner.runScenario(session)(s))
      scenarioFailsWithMessage(res) {
        """|Scenario 'scenario with JsonArraySteps' failed:
           |
           |at step:
           |test body array size is '0'
           |
           |with error(s):
           |expected array size '0' but actual size is '3' with array:
           |[
           |  "a",
           |  "b",
           |  "c"
           |]
           |
           |seed for the run was '1'
           |""".stripMargin
      }
    }

    test("JsonArrayStepBuilder has array size fail") {
      val session = Session.newEmpty.addValuesUnsafe(testKey -> """[ "a", "b", "c" ]""")
      val step = jsonStepBuilder.asArray.hasSize(2)
      val s = Scenario("scenario with JsonArraySteps", step :: Nil)
      val res = awaitTask(ScenarioRunner.runScenario(session)(s))
      scenarioFailsWithMessage(res) {
        """|Scenario 'scenario with JsonArraySteps' failed:
           |
           |at step:
           |test body array size is '2'
           |
           |with error(s):
           |expected array size '2' but actual size is '3' with array:
           |[
           |  "a",
           |  "b",
           |  "c"
           |]
           |
           |seed for the run was '1'
           |""".stripMargin
      }
    }

    test("JsonArrayStepBuilder is not ordered by default") {
      val session = Session.newEmpty.addValuesUnsafe(testKey -> """[ "a", "b", "c" ]""")
      val step = jsonStepBuilder.asArray.is("""[ "a", "c", "b" ]""")
      val s = Scenario("scenario with JsonArraySteps", step :: Nil)
      val res = awaitTask(ScenarioRunner.runScenario(session)(s))
      assert(res.isSuccess)
    }

    test("JsonArrayStepBuilder in order is") {
      val session = Session.newEmpty.addValuesUnsafe(testKey -> """[ "a", "b", "c" ]""")
      val step = jsonStepBuilder.asArray.inOrder.is("""[ "a", "c", "b" ]""")
      val s = Scenario("scenario with JsonArraySteps", step :: Nil)
      val res = awaitTask(ScenarioRunner.runScenario(session)(s))
      scenarioFailsWithMessage(res) {
        """|Scenario 'scenario with JsonArraySteps' failed:
           |
           |at step:
           |test body array in order is
           |[ "a", "c", "b" ]
           |
           |with error(s):
           |expected result was:
           |'Vector("a", "c", "b")'
           |but actual result is:
           |'Vector("a", "b", "c")'
           |
           |Ordered collection diff. between actual result and expected result is :
           |
           |
           |moved elements:
           |from index 1 to index 2
           |"c"
           |from index 2 to index 1
           |"b"
           |
           |seed for the run was '1'
           |""".stripMargin
      }
    }

    test("JsonArrayStepBuilder.contains") {
      val session = Session.newEmpty.addValuesUnsafe(testKey -> """[ "a", "b", "c" ]""")
      val step = jsonStepBuilder.asArray.contains("b", "c")
      val s = Scenario("scenario with JsonArraySteps", step :: Nil)
      val res = awaitTask(ScenarioRunner.runScenario(session)(s))
      assert(res.isSuccess)
    }

    test("JsonArrayStepBuilder.contains fail") {
      val session = Session.newEmpty.addValuesUnsafe(testKey -> """[ "a", "b", "c" ]""")
      val step = jsonStepBuilder.asArray.contains("d")
      val s = Scenario("scenario with JsonArraySteps", step :: Nil)
      val res = awaitTask(ScenarioRunner.runScenario(session)(s))
      scenarioFailsWithMessage(res) {
        """|Scenario 'scenario with JsonArraySteps' failed:
           |
           |at step:
           |test body array contains
           |d
           |
           |with error(s):
           |expected array to contain
           |'"d"'
           |but it is not the case with array:
           |[
           |  "a",
           |  "b",
           |  "c"
           |]
           |
           |seed for the run was '1'
           |""".stripMargin
      }
    }

    test("JsonArrayStepBuilder.contains fail - partial") {
      val session = Session.newEmpty.addValuesUnsafe(testKey -> """[ "a", "b", "c" ]""")
      val step = jsonStepBuilder.asArray.contains("c", "d")
      val s = Scenario("scenario with JsonArraySteps", step :: Nil)
      val res = awaitTask(ScenarioRunner.runScenario(session)(s))
      scenarioFailsWithMessage(res) {
        """|Scenario 'scenario with JsonArraySteps' failed:
           |
           |at step:
           |test body array contains
           |c and d
           |
           |with error(s):
           |expected array to contain
           |'"c" and "d"'
           |but it is not the case with array:
           |[
           |  "a",
           |  "b",
           |  "c"
           |]
           |
           |seed for the run was '1'
           |""".stripMargin
      }
    }

    test("JsonArrayStepBuilder.not contains fail") {
      val session = Session.newEmpty.addValuesUnsafe(testKey -> """[ "a", "b", "c" ]""")
      val step = jsonStepBuilder.asArray.not_contains("c")
      val s = Scenario("scenario with JsonArraySteps", step :: Nil)
      val res = awaitTask(ScenarioRunner.runScenario(session)(s))
      scenarioFailsWithMessage(res) {
        """|Scenario 'scenario with JsonArraySteps' failed:
           |
           |at step:
           |test body array does not contain
           |c
           |
           |with error(s):
           |expected array to not contain
           |'"c"'
           |but it is not the case with array:
           |[
           |  "a",
           |  "b",
           |  "c"
           |]
           |
           |seed for the run was '1'
           |""".stripMargin
      }
    }

    test("JsonArrayStepBuilder.not contains") {
      val session = Session.newEmpty.addValuesUnsafe(testKey -> """[ "a", "b", "c" ]""")
      val step = jsonStepBuilder.asArray.not_contains("d")
      val s = Scenario("scenario with JsonArraySteps", step :: Nil)
      val res = awaitTask(ScenarioRunner.runScenario(session)(s))
      assert(res.isSuccess)
    }

    test("JsonArrayStepBuilder.not contains - partial") {
      val session = Session.newEmpty.addValuesUnsafe(testKey -> """[ "a", "b", "c" ]""")
      val step = jsonStepBuilder.asArray.not_contains("c", "d")
      val s = Scenario("scenario with JsonArraySteps", step :: Nil)
      val res = awaitTask(ScenarioRunner.runScenario(session)(s))
      assert(res.isSuccess)
    }

    test("JsonArrayStepBuilder.containsExactly (identical)") {
      val session = Session.newEmpty.addValuesUnsafe(testKey -> """[ "a", "b", "c" ]""")
      val step = jsonStepBuilder.asArray.containsExactly("a", "b", "c")
      val s = Scenario("scenario with JsonArraySteps", step :: Nil)
      val res = awaitTask(ScenarioRunner.runScenario(session)(s))
      assert(res.isSuccess)
    }

    test("JsonArrayStepBuilder.containsExactly (out of order)") {
      val session = Session.newEmpty.addValuesUnsafe(testKey -> """[ "a", "b", "c" ]""")
      val step = jsonStepBuilder.asArray.containsExactly("b", "a", "c")
      val s = Scenario("scenario with JsonArraySteps", step :: Nil)
      val res = awaitTask(ScenarioRunner.runScenario(session)(s))
      assert(res.isSuccess)
    }

    test("JsonArrayStepBuilder.containsExactly fail (unknown element)") {
      val session = Session.newEmpty.addValuesUnsafe(testKey -> """[ "a", "b", "c" ]""")
      val step = jsonStepBuilder.asArray.containsExactly("d")
      val s = Scenario("scenario with JsonArraySteps", step :: Nil)
      val res = awaitTask(ScenarioRunner.runScenario(session)(s))
      scenarioFailsWithMessage(res) {
        """|Scenario 'scenario with JsonArraySteps' failed:
           |
           |at step:
           |test body array contains exactly
           |d
           |
           |with error(s):
           |Non ordered diff. between actual result and expected result is :
           |added elements:
           |"a"
           |"b"
           |"c"
           |deleted elements:
           |"d"
           |
           |seed for the run was '1'
           |""".stripMargin
      }
    }

    test("JsonArrayStepBuilder.containsExactly fail (missing element)") {
      val session = Session.newEmpty.addValuesUnsafe(testKey -> """[ "a", "b", "c" ]""")
      val step = jsonStepBuilder.asArray.containsExactly("a", "c")
      val s = Scenario("scenario with JsonArraySteps", step :: Nil)
      val res = awaitTask(ScenarioRunner.runScenario(session)(s))
      scenarioFailsWithMessage(res) {
        """|Scenario 'scenario with JsonArraySteps' failed:
           |
           |at step:
           |test body array contains exactly
           |a and c
           |
           |with error(s):
           |Non ordered diff. between actual result and expected result is :
           |added elements:
           |"b"
           |
           |seed for the run was '1'
           |""".stripMargin
      }
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

      scenarioFailsWithMessage(res) {
        """|Scenario 'scenario with JsonSteps' failed:
           |
           |at step:
           |test body array is
           |{ "myKeyOther" : *any-string* } ignoring keys $.*.myKey
           |
           |with error(s):
           |matchers are not supported in `asArray` assertion but *any-string* found
           |https://github.com/agourlay/cornichon/issues/135
           |
           |seed for the run was '1'
           |""".stripMargin
      }
    }
  }
}
