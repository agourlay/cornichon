package com.github.agourlay.cornichon.json

import com.github.agourlay.cornichon.core.{Scenario, ScenarioRunner, Session}
import com.github.agourlay.cornichon.json.JsonSteps.JsonValuesStepBuilder
import com.github.agourlay.cornichon.testHelpers.IOSpec
import io.circe.{Json, JsonObject}
import io.circe.testing.ArbitraryInstances
import org.scalacheck.Prop.forAll
import org.scalacheck.Properties

class JsonValuesStepProperties extends Properties("JsonValuesSteps") with ArbitraryInstances with IOSpec {

  private val testKey = "test-key"
  private val otherTestKey = "other-test-key"
  private val jsonValuesStepBuilder = JsonValuesStepBuilder(testKey, otherTestKey)

  property("JsonValuesStepBuild are equals") = forAll { (jsonOb: JsonObject) =>
    val json = Json.fromJsonObject(jsonOb).spaces2
    val session = Session.newEmpty
      .addValuesUnsafe(testKey -> json)
      .addValuesUnsafe(otherTestKey -> json)
    val step = jsonValuesStepBuilder.areEquals
    val s = Scenario("scenario with JsonValuesSteps", step :: Nil)
    val t = awaitIO(ScenarioRunner.runScenario(session)(s))
    t.isSuccess
  }

  property("JsonValuesStepBuild are not equals") = forAll { (jsonOb: JsonObject) =>
    val json = Json.fromJsonObject(jsonOb).spaces2
    val jsonDifferent = Json.fromJsonObject(jsonOb.add("__extra__", Json.fromString("something"))).spaces2
    val session = Session.newEmpty
      .addValuesUnsafe(testKey -> json)
      .addValuesUnsafe(otherTestKey -> jsonDifferent)
    val step = jsonValuesStepBuilder.areNotEquals
    val s = Scenario("scenario with JsonValuesSteps", step :: Nil)
    val t = awaitIO(ScenarioRunner.runScenario(session)(s))
    t.isSuccess
  }

  property("JsonValuesStepBuild are equals with ignoring fields") = forAll { (jsonOb: JsonObject) =>
    val json = Json.fromJsonObject(jsonOb)
    val jsonBonus = Json.fromJsonObject(jsonOb.add("additional", Json.fromString("bonus to ignore")))
    val session = Session.newEmpty
      .addValuesUnsafe(testKey -> json.spaces2)
      .addValuesUnsafe(otherTestKey -> jsonBonus.spaces2)
    val step = jsonValuesStepBuilder.ignoring("additional").areEquals
    val s = Scenario("scenario with JsonValuesSteps", step :: Nil)
    val t = awaitIO(ScenarioRunner.runScenario(session)(s))
    t.isSuccess
  }

  property("JsonValuesStepBuild are equals with ignoring fields - failure") = forAll { (jsonOb: JsonObject) =>
    val json = Json.fromJsonObject(jsonOb.add("other-detail", Json.fromString("important thing")))
    val jsonBonus = Json.fromJsonObject(jsonOb.add("additional", Json.fromString("bonus to ignore")))
    val session = Session.newEmpty
      .addValuesUnsafe(testKey -> json.spaces2)
      .addValuesUnsafe(otherTestKey -> jsonBonus.spaces2)
    val step = jsonValuesStepBuilder.ignoring("additional").areEquals
    val s = Scenario("scenario with JsonValuesSteps", step :: Nil)
    val t = awaitIO(ScenarioRunner.runScenario(session)(s))
    !t.isSuccess
  }

  property("JsonValuesStepBuild are equals with focus on path") = forAll { (jsonOb: JsonObject) =>
    val json = Json.fromJsonObject(jsonOb.add("important", Json.fromString("important thing")))
    val jsonBonus = Json.fromJsonObject(
      jsonOb
        .add("important", Json.fromString("important thing"))
        .add("additional", Json.fromString("bonus to ignore"))
    )
    val session = Session.newEmpty
      .addValuesUnsafe(testKey -> json.spaces2)
      .addValuesUnsafe(otherTestKey -> jsonBonus.spaces2)
    val step = jsonValuesStepBuilder.path("important").areEquals
    val s = Scenario("scenario with JsonValuesSteps", step :: Nil)
    val t = awaitIO(ScenarioRunner.runScenario(session)(s))
    t.isSuccess
  }

  property("JsonValuesStepBuild comparing value with itself always succeeds") = forAll { (jsonOb: JsonObject) =>
    val json = Json.fromJsonObject(jsonOb)
    val session = Session.newEmpty
      .addValuesUnsafe(testKey -> json.spaces2)
      .addValuesUnsafe(otherTestKey -> json.spaces2)
    val step = jsonValuesStepBuilder.areEquals
    val s = Scenario("scenario with self-comparison", step :: Nil)
    val t = awaitIO(ScenarioRunner.runScenario(session)(s))
    t.isSuccess
  }

  property("JsonValuesStepBuild are equals with JSON arrays") = {
    val array = """[1, 2, 3]"""
    val session = Session.newEmpty
      .addValuesUnsafe(testKey -> array)
      .addValuesUnsafe(otherTestKey -> array)
    val step = jsonValuesStepBuilder.areEquals
    val s = Scenario("scenario with array comparison", step :: Nil)
    val t = awaitIO(ScenarioRunner.runScenario(session)(s))
    t.isSuccess
  }

  property("JsonValuesStepBuild are equals with focus on path - failure") = forAll { (jsonOb: JsonObject) =>
    val json = Json.fromJsonObject(jsonOb.add("important", Json.fromString("important thing")))
    val jsonBonus = Json.fromJsonObject(
      jsonOb
        .add("important", Json.fromString("very important thing"))
        .add("additional", Json.fromString("bonus to ignore"))
    )
    val session = Session.newEmpty
      .addValuesUnsafe(testKey -> json.spaces2)
      .addValuesUnsafe(otherTestKey -> jsonBonus.spaces2)
    val step = jsonValuesStepBuilder.path("important").areEquals
    val s = Scenario("scenario with JsonValuesSteps", step :: Nil)
    val t = awaitIO(ScenarioRunner.runScenario(session)(s))
    !t.isSuccess
  }

}
