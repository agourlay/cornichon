package com.github.agourlay.cornichon.json

import com.github.agourlay.cornichon.core._
import com.github.agourlay.cornichon.json.JsonSteps.JsonStepBuilder
import com.github.agourlay.cornichon.steps.StepUtilSpec
import io.circe.{ Json, JsonObject }
import io.circe.testing.ArbitraryInstances
import org.scalatest.{ AsyncWordSpec, Matchers, OptionValues }
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks

import scala.concurrent.Await
import scala.concurrent.duration.Duration

class JsonStepsSpec extends AsyncWordSpec
  with ScalaCheckPropertyChecks
  with ArbitraryInstances
  with Matchers
  with OptionValues
  with StepUtilSpec {

  private val testKey = "test-key"
  private val jsonStepBuilder = JsonStepBuilder(SessionKey(testKey), Some("test body"))

  "JsonStep" when {
    "JsonStepBuilder" must {
      "is value" in {
        val session = Session.newEmpty.addValuesUnsafe(testKey -> "test")
        val step = jsonStepBuilder.is("test")
        val s = Scenario("scenario with JsonSteps", step :: Nil)
        ScenarioRunner.runScenario(session)(s).map { r ⇒
          r.isSuccess should be(true)
        }
      }

      "is value fail" in {
        val session = Session.newEmpty.addValuesUnsafe(testKey -> "test")
        val step = jsonStepBuilder.isNot("test")
        val s = Scenario("scenario with JsonSteps", step :: Nil)
        ScenarioRunner.runScenario(session)(s).map { r ⇒
          r.isSuccess should be(false)
        }
      }

      "is malformed Json fail" in {
        val session = Session.newEmpty.addValuesUnsafe(testKey -> "test")
        val step = jsonStepBuilder.is("{test:")
        val s = Scenario("scenario with JsonSteps", step :: Nil)
        ScenarioRunner.runScenario(session)(s).map { r ⇒
          r.isSuccess should be(false)
        }
      }
      "isNot value" in {
        val session = Session.newEmpty.addValuesUnsafe(testKey -> "test")
        val step = jsonStepBuilder.isNot("test1")
        val s = Scenario("scenario with JsonSteps", step :: Nil)
        ScenarioRunner.runScenario(session)(s).map { r ⇒
          r.isSuccess should be(true)
        }
      }

      "isNot value fail" in {
        val session = Session.newEmpty.addValuesUnsafe(testKey -> "test")
        val step = jsonStepBuilder.isNot("test")
        val s = Scenario("scenario with JsonSteps", step :: Nil)
        ScenarioRunner.runScenario(session)(s).map { r ⇒
          r.isSuccess should be(false)
        }
      }

      "is path to json key" in {
        val session = Session.newEmpty.addValuesUnsafe(testKey -> """{ "myKey" : "myValue" }""")
        val step = jsonStepBuilder.path("myKey").is("myValue")
        val s = Scenario("scenario with JsonSteps", step :: Nil)
        ScenarioRunner.runScenario(session)(s).map { r ⇒
          r.isSuccess should be(true)
        }
      }

      "isPresent path to json key" in {
        val session = Session.newEmpty.addValuesUnsafe(testKey -> """{ "myKey" : "myValue" }""")
        val step = jsonStepBuilder.path("myKey").isPresent
        val s = Scenario("scenario with JsonSteps", step :: Nil)
        ScenarioRunner.runScenario(session)(s).map { r ⇒
          r.isSuccess should be(true)
        }
      }

      "isPresent path to json key fail" in {
        val session = Session.newEmpty.addValuesUnsafe(testKey -> """{ "myKey" : "myValue" }""")
        val step = jsonStepBuilder.path("myKey2").isPresent
        val s = Scenario("scenario with JsonSteps", step :: Nil)
        ScenarioRunner.runScenario(session)(s).map { r ⇒
          r.isSuccess should be(false)
        }
      }

      "isAbsent path to json key" in {
        val session = Session.newEmpty.addValuesUnsafe(testKey -> """{ "myKey" : "myValue" }""")
        val step = jsonStepBuilder.path("myKey2").isAbsent
        val s = Scenario("scenario with JsonSteps", step :: Nil)
        ScenarioRunner.runScenario(session)(s).map { r ⇒
          r.isSuccess should be(true)
        }
      }

      "isAbsent path to json key fail" in {
        val session = Session.newEmpty.addValuesUnsafe(testKey -> """{ "myKey" : "myValue" }""")
        val step = jsonStepBuilder.path("myKey").isAbsent
        val s = Scenario("scenario with JsonSteps", step :: Nil)
        ScenarioRunner.runScenario(session)(s).map { r ⇒
          r.isSuccess should be(false)
        }
      }

      "isNull path to json key" in {
        val session = Session.newEmpty.addValuesUnsafe(testKey -> """{ "myKey" : null }""")
        val step = jsonStepBuilder.path("myKey").isNull
        val s = Scenario("scenario with JsonSteps", step :: Nil)
        ScenarioRunner.runScenario(session)(s).map { r ⇒
          r.isSuccess should be(true)
        }
      }

      "isNull path to json key fail" in {
        val session = Session.newEmpty.addValuesUnsafe(testKey -> """{ "myKey" : "notNull" }""")
        val step = jsonStepBuilder.path("myKey").isNull
        val s = Scenario("scenario with JsonSteps", step :: Nil)
        ScenarioRunner.runScenario(session)(s).map { r ⇒
          r.isSuccess should be(false)
        }
      }

      "isNotNull path to json key" in {
        val session = Session.newEmpty.addValuesUnsafe(testKey -> """{ "myKey" : "null" }""")
        val step = jsonStepBuilder.path("myKey").isNotNull
        val s = Scenario("scenario with JsonSteps", step :: Nil)
        ScenarioRunner.runScenario(session)(s).map { r ⇒
          r.isSuccess should be(true)
        }
      }

      "isNotNull path to json key fail" in {
        val session = Session.newEmpty.addValuesUnsafe(testKey -> """{ "myKey" : null }""")
        val step = jsonStepBuilder.path("myKey").isNotNull
        val s = Scenario("scenario with JsonSteps", step :: Nil)
        ScenarioRunner.runScenario(session)(s).map { r ⇒
          r.isSuccess should be(false)
        }
      }

      "is with absent path to json key" in {
        val session = Session.newEmpty.addValuesUnsafe(testKey -> """{ "myKey" : "myValue" }""")
        val step = jsonStepBuilder.path("myKey1").is("myValue")
        val s = Scenario("scenario with JsonSteps", step :: Nil)
        ScenarioRunner.runScenario(session)(s).map { r ⇒
          r.isSuccess should be(false)
        }
      }

      "is json with ignore" in {
        val session = Session.newEmpty.addValuesUnsafe(testKey -> """{ "myKey" : "myValue", "myKeyOther" : "myOtherValue" }""")
        val step = jsonStepBuilder.ignoring("myKey").is("""{ "myKeyOther" : "myOtherValue" }""")
        val s = Scenario("scenario with JsonSteps", step :: Nil)
        ScenarioRunner.runScenario(session)(s).map { r ⇒
          r.isSuccess should be(true)
        }
      }

      "is json with ignore provided in expected anyway" in {
        val session = Session.newEmpty.addValuesUnsafe(testKey -> """{ "myKey" : "myValue", "myKeyOther" : "myOtherValue" }""")
        val step = jsonStepBuilder.ignoring("myKey").is("""{ "myKey" : "myValue", "myKeyOther" : "myOtherValue" }""")
        val s = Scenario("scenario with JsonSteps", step :: Nil)
        ScenarioRunner.runScenario(session)(s).map { r ⇒
          r.isSuccess should be(true)
        }
      }

      "is json ignoring absent key does not fail" in {
        val session = Session.newEmpty.addValuesUnsafe(testKey -> """{ "myKey" : "myValue", "myKeyOther" : "myOtherValue" }""")
        val step = jsonStepBuilder.ignoring("myKey", "myKey1").is("""{ "myKeyOther" : "myOtherValue" }""")
        val s = Scenario("scenario with JsonSteps", step :: Nil)
        ScenarioRunner.runScenario(session)(s).map { r ⇒
          r.isSuccess should be(true)
        }
      }

      "is json with whitelisting" in {
        val session = Session.newEmpty.addValuesUnsafe(testKey -> """{ "myKey" : "myValue", "myKeyOther" : "myOtherValue" }""")
        val step = jsonStepBuilder.whitelisting.is("""{ "myKeyOther" : "myOtherValue" }""")
        val s = Scenario("scenario with JsonSteps", step :: Nil)
        ScenarioRunner.runScenario(session)(s).map { r ⇒
          r.isSuccess should be(true)
        }
      }

      "is json without whitelisting enabled" in {
        val session = Session.newEmpty.addValuesUnsafe(testKey -> """{ "myKey" : "myValue", "myKeyOther" : "myOtherValue" }""")
        // forcing whitelisting to false to have the negative test
        val step = jsonStepBuilder.copy(whitelist = false).is("""{ "myKeyOther" : "myOtherValue" }""")
        val s = Scenario("scenario with JsonSteps", step :: Nil)
        ScenarioRunner.runScenario(session)(s).map { r ⇒
          r.isSuccess should be(false)
        }
      }

      "is json with ignoring fields and whitelisting - combination not supported" in {
        val session = Session.newEmpty.addValuesUnsafe(testKey -> """{ "myKey" : "myValue", "myKeyOther" : "myOtherValue" }""")
        val step = jsonStepBuilder.whitelisting.ignoring("blah").is("""{ "myKeyOther" : "myOtherValue" }""")
        val s = Scenario("scenario with JsonSteps", step :: Nil)
        ScenarioRunner.runScenario(session)(s).map {
          case f: FailureScenarioReport ⇒
            f.failedSteps.head.errors.head.renderedMessage should be(
              """usage of 'ignoring' and 'whiteListing' is mutually exclusive"""
            )
          case other ⇒ fail(s"Should be a FailedScenarioReport but got \n${other.logs}")
        }
      }

      "is json with matcher in string" in {
        val session = Session.newEmpty.addValuesUnsafe(testKey -> "myOtherValue")
        val step = jsonStepBuilder.is("*any-string*")
        val s = Scenario("scenario with JsonSteps", step :: Nil)
        ScenarioRunner.runScenario(session)(s).map { r ⇒
          r.isSuccess should be(true)
        }
      }

      "is json with matcher in string - non trimmed" in {
        val session = Session.newEmpty.addValuesUnsafe(testKey -> "myOtherValue")
        val step = jsonStepBuilder.is(" *any-string* ")
        val s = Scenario("scenario with JsonSteps", step :: Nil)
        ScenarioRunner.runScenario(session)(s).map { r ⇒
          r.isSuccess should be(false)
        }
      }

      "is json with matcher in object" in {
        val session = Session.newEmpty.addValuesUnsafe(testKey -> """{ "myKey" : 1, "myKeyOther" : "myOtherValue" }""")
        val step = jsonStepBuilder.is("""{ "myKey" : *any-integer*, "myKeyOther" : *any-string* }""")
        val s = Scenario("scenario with JsonSteps", step :: Nil)
        ScenarioRunner.runScenario(session)(s).map { r ⇒
          r.isSuccess should be(true)
        }
      }

      "is json with matchers in array" in {
        val session = Session.newEmpty.addValuesUnsafe(testKey -> """[{ "myKey" : 1, "myKeyOther" : "myOtherValue" }]""")
        val step = jsonStepBuilder.is("""[{ "myKey" : *any-integer*, "myKeyOther" : *any-string* }]""")
        val s = Scenario("scenario with JsonSteps", step :: Nil)
        ScenarioRunner.runScenario(session)(s).map { r ⇒
          r.isSuccess should be(true)
        }
      }

      "is any Circe jsonObject" in {
        forAll { jsonOb: JsonObject ⇒
          val json = Json.fromJsonObject(jsonOb)
          val session = Session.newEmpty.addValuesUnsafe(testKey -> json.spaces2)
          val step = jsonStepBuilder.is(json)
          val s = Scenario("scenario with JsonSteps", step :: Nil)
          val t = ScenarioRunner.runScenario(session)(s).map { r ⇒
            withClue(LogInstruction.renderLogs(r.logs)) {
              r.isSuccess should be(true)
            }
          }
          Await.result(t, Duration.Inf)
        }
      }

      "is any Circe jsonObject with placeholder" in {
        forAll { jsonOb: JsonObject ⇒
          val fullJsonObj = jsonOb.add("myKeyOther", Json.fromString("myOtherValue"))
          val session = Session.newEmpty.addValuesUnsafe(
            testKey -> Json.fromJsonObject(fullJsonObj).spaces2,
            "a-placeholder" -> "myOtherValue"
          )
          val fullPlaceholderJsonObj = jsonOb.add("myKeyOther", Json.fromString("<a-placeholder>"))
          val step = jsonStepBuilder.is(Json.fromJsonObject(fullPlaceholderJsonObj))
          val s = Scenario("scenario with JsonSteps", step :: Nil)
          val t = ScenarioRunner.runScenario(session)(s).map { r ⇒
            withClue(LogInstruction.renderLogs(r.logs)) {
              r.isSuccess should be(true)
            }
          }
          Await.result(t, Duration.Inf)
        }
      }

      "is Circe jsonObject with absent placeholder" in {
        forAll { jsonOb: JsonObject ⇒
          val fullJsonObj = jsonOb.add("myKeyOther", Json.fromString("myOtherValue"))
          val session = Session.newEmpty.addValuesUnsafe(testKey -> Json.fromJsonObject(fullJsonObj).spaces2)
          val fullPlaceholderJsonObj = jsonOb.add("myKeyOther", Json.fromString("<a-placeholder>"))
          val step = jsonStepBuilder.is(Json.fromJsonObject(fullPlaceholderJsonObj))
          val s = Scenario("scenario with JsonSteps", step :: Nil)
          val t = ScenarioRunner.runScenario(session)(s).map { r ⇒
            withClue(LogInstruction.renderLogs(r.logs)) {
              r.isSuccess should be(false)
            }
          }
          Await.result(t, Duration.Inf)
        }
      }

      "is case class asJson with placeholder" in {
        import io.circe.generic.auto._
        import io.circe.syntax._
        case class MyObj(myKey: String, myKeyOther: String)

        val jsonString = """{ "myKey" : "myValue", "myKeyOther" : "myOtherValue" }"""
        val session = Session.newEmpty.addValuesUnsafe(testKey -> jsonString, "a-placeholder" -> "myOtherValue")

        val instance = MyObj("myValue", "<a-placeholder>")

        val step = jsonStepBuilder.is(instance.asJson)
        val s = Scenario("scenario with JsonSteps", step :: Nil)
        ScenarioRunner.runScenario(session)(s).map { r ⇒
          r.isSuccess should be(true)
        }
      }
    }

    "JsonArrayStepBuilder" must {
      "is not an array fail" in {
        val session = Session.newEmpty.addValuesUnsafe(testKey -> "test")
        val step = jsonStepBuilder.asArray.is("test")
        val s = Scenario("scenario with JsonArraySteps", step :: Nil)
        ScenarioRunner.runScenario(session)(s).map { r ⇒
          r.isSuccess should be(false)
        }
      }

      "is array" in {
        val session = Session.newEmpty.addValuesUnsafe(testKey -> """["a", "b", "c" ]""")
        val step = jsonStepBuilder.asArray.is("""["a", "b". "c" ]""")
        val s = Scenario("scenario with JsonArraySteps", step :: Nil)
        ScenarioRunner.runScenario(session)(s).map { r ⇒
          r.isSuccess should be(false)
        }
      }

      "has array size" in {
        val session = Session.newEmpty.addValuesUnsafe(testKey -> """["a", "b", "c" ]""")
        val step = jsonStepBuilder.asArray.hasSize(3)
        val s = Scenario("scenario with JsonArraySteps", step :: Nil)
        ScenarioRunner.runScenario(session)(s).map { r ⇒
          r.isSuccess should be(true)
        }
      }

      // goes through the new experimental GenericAssertStepBuilder
      "array size is" in {
        val session = Session.newEmpty.addValuesUnsafe(testKey -> """["a", "b", "c" ]""")
        val step = jsonStepBuilder.asArray.size.is(3)
        val s = Scenario("scenario with JsonArraySteps", step :: Nil)
        ScenarioRunner.runScenario(session)(s).map { r ⇒
          r.isSuccess should be(true)
        }
      }

      "array size is greater" in {
        val session = Session.newEmpty.addValuesUnsafe(testKey -> """["a", "b", "c" ]""")
        val step = jsonStepBuilder.asArray.size.isGreaterThan(2)
        val s = Scenario("scenario with JsonArraySteps", step :: Nil)
        ScenarioRunner.runScenario(session)(s).map { r ⇒
          r.isSuccess should be(true)
        }
      }

      "array size is less" in {
        val session = Session.newEmpty.addValuesUnsafe(testKey -> """["a", "b", "c" ]""")
        val step = jsonStepBuilder.asArray.size.isLessThan(4)
        val s = Scenario("scenario with JsonArraySteps", step :: Nil)
        ScenarioRunner.runScenario(session)(s).map { r ⇒
          r.isSuccess should be(true)
        }
      }

      "array size is between" in {
        val session = Session.newEmpty.addValuesUnsafe(testKey -> """["a", "b", "c" ]""")
        val step = jsonStepBuilder.asArray.size.isBetween(2, 4)
        val s = Scenario("scenario with JsonArraySteps", step :: Nil)
        ScenarioRunner.runScenario(session)(s).map { r ⇒
          r.isSuccess should be(true)
        }
      }

      "not empty array" in {
        val session = Session.newEmpty.addValuesUnsafe(testKey -> """["a", "b", "c" ]""")
        val step = jsonStepBuilder.asArray.isNotEmpty
        val s = Scenario("scenario with JsonArraySteps", step :: Nil)
        ScenarioRunner.runScenario(session)(s).map { r ⇒
          r.isSuccess should be(true)
        }
      }

      "not empty array fail" in {
        val session = Session.newEmpty.addValuesUnsafe(testKey -> """[]""")
        val step = jsonStepBuilder.asArray.isNotEmpty
        val s = Scenario("scenario with JsonArraySteps", step :: Nil)
        ScenarioRunner.runScenario(session)(s).map { r ⇒
          r.isSuccess should be(false)
        }
      }

      "is empty array" in {
        val session = Session.newEmpty.addValuesUnsafe(testKey -> """[]""")
        val step = jsonStepBuilder.asArray.isEmpty
        val s = Scenario("scenario with JsonArraySteps", step :: Nil)
        ScenarioRunner.runScenario(session)(s).map { r ⇒
          r.isSuccess should be(true)
        }
      }

      "is empty array fail" in {
        val session = Session.newEmpty.addValuesUnsafe(testKey -> """["a", "b", "c" ]""")
        val step = jsonStepBuilder.asArray.isEmpty
        val s = Scenario("scenario with JsonArraySteps", step :: Nil)
        ScenarioRunner.runScenario(session)(s).map { r ⇒
          r.isSuccess should be(false)
        }
      }

      "has array size fail" in {
        val session = Session.newEmpty.addValuesUnsafe(testKey -> """["a", "b", "c" ]""")
        val step = jsonStepBuilder.asArray.hasSize(2)
        val s = Scenario("scenario with JsonArraySteps", step :: Nil)
        ScenarioRunner.runScenario(session)(s).map { r ⇒
          r.isSuccess should be(false)
        }
      }

      "is not ordered by default" in {
        val session = Session.newEmpty.addValuesUnsafe(testKey -> """["a", "b", "c" ]""")
        val step = jsonStepBuilder.asArray.is("""["a", "c", "b" ]""")
        val s = Scenario("scenario with JsonArraySteps", step :: Nil)
        ScenarioRunner.runScenario(session)(s).map { r ⇒
          r.isSuccess should be(true)
        }
      }

      "is in order by default" in {
        val session = Session.newEmpty.addValuesUnsafe(testKey -> """["a", "b", "c" ]""")
        val step = jsonStepBuilder.asArray.inOrder.is("""["a", "c", "b" ]""")
        val s = Scenario("scenario with JsonArraySteps", step :: Nil)
        ScenarioRunner.runScenario(session)(s).map { r ⇒
          r.isSuccess should be(false)
        }
      }

      "contains" in {
        val session = Session.newEmpty.addValuesUnsafe(testKey -> """["a", "b", "c" ]""")
        val step = jsonStepBuilder.asArray.contains("b", "c")
        val s = Scenario("scenario with JsonArraySteps", step :: Nil)
        ScenarioRunner.runScenario(session)(s).map { r ⇒
          r.isSuccess should be(true)
        }
      }

      "contains fail" in {
        val session = Session.newEmpty.addValuesUnsafe(testKey -> """["a", "b", "c" ]""")
        val step = jsonStepBuilder.asArray.contains("d")
        val s = Scenario("scenario with JsonArraySteps", step :: Nil)
        ScenarioRunner.runScenario(session)(s).map { r ⇒
          r.isSuccess should be(false)
        }
      }

      "contains fail - partial" in {
        val session = Session.newEmpty.addValuesUnsafe(testKey -> """["a", "b", "c" ]""")
        val step = jsonStepBuilder.asArray.contains("c", "d")
        val s = Scenario("scenario with JsonArraySteps", step :: Nil)
        ScenarioRunner.runScenario(session)(s).map { r ⇒
          r.isSuccess should be(false)
        }
      }

      "not contains fail" in {
        val session = Session.newEmpty.addValuesUnsafe(testKey -> """["a", "b", "c" ]""")
        val step = jsonStepBuilder.asArray.not_contains("d")
        val s = Scenario("scenario with JsonArraySteps", step :: Nil)
        ScenarioRunner.runScenario(session)(s).map { r ⇒
          r.isSuccess should be(true)
        }
      }

      "not contains fail - partial" in {
        val session = Session.newEmpty.addValuesUnsafe(testKey -> """["a", "b", "c" ]""")
        val step = jsonStepBuilder.asArray.not_contains("c", "d")
        val s = Scenario("scenario with JsonArraySteps", step :: Nil)
        ScenarioRunner.runScenario(session)(s).map { r ⇒
          r.isSuccess should be(true)
        }
      }

      "not contains" in {
        val session = Session.newEmpty.addValuesUnsafe(testKey -> """["a", "b", "c" ]""")
        val step = jsonStepBuilder.asArray.not_contains("d")
        val s = Scenario("scenario with JsonArraySteps", step :: Nil)
        ScenarioRunner.runScenario(session)(s).map { r ⇒
          r.isSuccess should be(true)
        }
      }

      "is json with ignoreEach" in {
        val session = Session.newEmpty.addValuesUnsafe(testKey -> """[{ "myKey" : "myValue", "myKeyOther" : "myOtherValue" }]""")
        val step = jsonStepBuilder.asArray.ignoringEach("myKey").is("""[{ "myKeyOther" : "myOtherValue" }]""")
        val s = Scenario("scenario with JsonSteps", step :: Nil)
        ScenarioRunner.runScenario(session)(s).map { r ⇒
          r.isSuccess should be(true)
        }
      }

      "is json with ignoreEach provided in expected anyway" in {
        val session = Session.newEmpty.addValuesUnsafe(testKey -> """[{ "myKey" : "myValue", "myKeyOther" : "myOtherValue" }]""")
        val step = jsonStepBuilder.asArray.ignoringEach("myKey").is("""[{ "myKey" : "myValue", "myKeyOther" : "myOtherValue" }]""")
        val s = Scenario("scenario with JsonSteps", step :: Nil)
        ScenarioRunner.runScenario(session)(s).map { r ⇒
          r.isSuccess should be(true)
        }
      }

      "is json with matcher" in {
        val session = Session.newEmpty.addValuesUnsafe(testKey -> """[{ "myKey" : "myValue", "myKeyOther" : "myOtherValue" }]""")
        val step = jsonStepBuilder.asArray.ignoringEach("$.*.myKey").is("""{ "myKeyOther" : *any-string* }""")
        val s = Scenario("scenario with JsonSteps", step :: Nil)
        ScenarioRunner.runScenario(session)(s).map {
          case f: FailureScenarioReport ⇒
            f.failedSteps.head.errors.head.renderedMessage should be(
              """
                |matchers are not supported in `asArray` assertion but *any-string* found
                |https://github.com/agourlay/cornichon/issues/135"""
                .stripMargin.trim
            )
          case other ⇒ fail(s"Should be a FailedScenarioReport but got \n${other.logs}")
        }
      }
    }
  }
}
