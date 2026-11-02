package com.github.agourlay.cornichon.json

import com.github.agourlay.cornichon.core.{Scenario, ScenarioRunner, Session, SessionKey}
import com.github.agourlay.cornichon.json.JsonSteps.JsonStepBuilder
import com.github.agourlay.cornichon.testHelpers.IOSpec
import io.circe.{Json, JsonObject}
import io.circe.testing.ArbitraryInstances
import org.scalacheck.{Arbitrary, Gen, Properties}
import org.scalacheck.Prop._

class JsonStepsProperties extends Properties("JsonSteps") with ArbitraryInstances with IOSpec {

  private val testKey = "test-key"
  private val jsonStepBuilder = JsonStepBuilder(SessionKey(testKey), Some("test body"))

  // The DSL parses strings whose first non-whitespace char is '[' or '{' as JSON, so exclude them
  private val plainStringGen: Gen[String] =
    Arbitrary.arbitrary[String].filter { s =>
      s.dropWhile(_.isWhitespace).headOption.forall(c => c != '[' && c != '{')
    }

  property("JsonStepBuild is value") = forAll(plainStringGen) { (input: String) =>
    val session = Session.newEmpty.addValuesUnsafe(testKey -> input)
    val step = jsonStepBuilder.is(input)
    val s = Scenario("scenario with JsonSteps", step :: Nil)
    val t = awaitIO(ScenarioRunner.runScenario(session)(s))
    t.isSuccess
  }

  property("JsonStepBuild is value fail") = forAll(plainStringGen) { (input: String) =>
    val session = Session.newEmpty.addValuesUnsafe(testKey -> input)
    val step = jsonStepBuilder.is(input + "42")
    val s = Scenario("scenario with JsonSteps", step :: Nil)
    val t = awaitIO(ScenarioRunner.runScenario(session)(s))
    !t.isSuccess
  }

  property("JsonStepBuild isNot value") = forAll(plainStringGen) { (input: String) =>
    val session = Session.newEmpty.addValuesUnsafe(testKey -> input)
    val step = jsonStepBuilder.isNot(input + "42")
    val s = Scenario("scenario with JsonSteps", step :: Nil)
    val t = awaitIO(ScenarioRunner.runScenario(session)(s))
    t.isSuccess
  }

  property("JsonStepBuild isNot value fail") = forAll(plainStringGen) { (input: String) =>
    val session = Session.newEmpty.addValuesUnsafe(testKey -> input)
    val step = jsonStepBuilder.isNot(input)
    val s = Scenario("scenario with JsonSteps", step :: Nil)
    val t = awaitIO(ScenarioRunner.runScenario(session)(s))
    !t.isSuccess
  }

  property("JsonStepBuilder is any Circe jsonObject") = forAll { (jsonOb: JsonObject) =>
    val json = Json.fromJsonObject(jsonOb)
    val session = Session.newEmpty.addValuesUnsafe(testKey -> json.spaces2)
    val step = jsonStepBuilder.is(json)
    val s = Scenario("scenario with JsonSteps", step :: Nil)
    val t = awaitIO(ScenarioRunner.runScenario(session)(s))
    t.isSuccess
  }

  property("JsonStepBuilder is any Circe jsonObject with placeholder") = forAll { (jsonOb: JsonObject) =>
    val fullJsonObj = jsonOb.add("myKeyOther", Json.fromString("myOtherValue"))
    val session = Session.newEmpty.addValuesUnsafe(
      testKey         -> Json.fromJsonObject(fullJsonObj).spaces2,
      "a-placeholder" -> "myOtherValue"
    )
    val fullPlaceholderJsonObj = jsonOb.add("myKeyOther", Json.fromString("<a-placeholder>"))
    val step = jsonStepBuilder.is(Json.fromJsonObject(fullPlaceholderJsonObj))
    val s = Scenario("scenario with JsonSteps", step :: Nil)
    val t = awaitIO(ScenarioRunner.runScenario(session)(s))
    t.isSuccess
  }

  property("JsonStepBuilder is Circe jsonObject with absent placeholder") = forAll { (jsonOb: JsonObject) =>
    val fullJsonObj = jsonOb.add("myKeyOther", Json.fromString("myOtherValue"))
    val session = Session.newEmpty.addValuesUnsafe(testKey -> Json.fromJsonObject(fullJsonObj).spaces2)
    val fullPlaceholderJsonObj = jsonOb.add("myKeyOther", Json.fromString("<a-placeholder>"))
    val step = jsonStepBuilder.is(Json.fromJsonObject(fullPlaceholderJsonObj))
    val s = Scenario("scenario with JsonSteps", step :: Nil)
    val t = awaitIO(ScenarioRunner.runScenario(session)(s))
    !t.isSuccess
  }

}
