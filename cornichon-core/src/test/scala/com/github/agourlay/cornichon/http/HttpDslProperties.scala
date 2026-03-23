package com.github.agourlay.cornichon.http

import com.github.agourlay.cornichon.core.{Scenario, ScenarioRunner, Session, SessionKey}
import com.github.agourlay.cornichon.dsl.SessionSteps.SessionStepBuilder
import com.github.agourlay.cornichon.json.JsonPath
import com.github.agourlay.cornichon.json.CornichonJson.jsonStringValue
import com.github.agourlay.cornichon.testHelpers.IOSpec
import org.scalacheck.{Gen, Properties}
import org.scalacheck.Prop._

class HttpDslProperties extends Properties("HttpDsl") with IOSpec {

  private val ops = new HttpDslOps {}

  property("removeFromWithHeaders handle no 'with-headers'") = forAll(Gen.alphaStr) { header =>
    ops.removeFromWithHeaders(header)(Session.newEmpty) == Right(Session.newEmpty)
  }

  property("save_body accepts to save any String as a body") = forAll { (input: String) =>
    val session = Session.newEmpty.addValuesUnsafe(HttpDsl.lastBodySessionKey.name -> input)
    val saveStep = HttpDsl.save_body("new-key")
    val assertStep = SessionStepBuilder(SessionKey("new-key")).is(input)
    val s = Scenario("scenario with any save_body", saveStep :: assertStep :: Nil)
    val t = awaitIO(ScenarioRunner.runScenario(session)(s))
    t.isSuccess
  }

  property("JsonPath extracts nested values from session body") = {
    val jsonBody = """{"user": {"name": "Batman", "city": "Gotham"}}"""
    val result = for {
      json <- com.github.agourlay.cornichon.json.CornichonJson.parseDslJson(jsonBody)
      path <- JsonPath.parse("$.user.name")
      value <- path.runStrict(json)
    } yield jsonStringValue(value)
    result == Right("Batman")
  }

  property("JsonPath extracts array elements from session body") = {
    val jsonBody = """{"items": ["a", "b", "c"]}"""
    val result = for {
      json <- com.github.agourlay.cornichon.json.CornichonJson.parseDslJson(jsonBody)
      path <- JsonPath.parse("$.items[1]")
      value <- path.runStrict(json)
    } yield jsonStringValue(value)
    result == Right("b")
  }

  property("JsonPath returns error for non-existent path") = {
    val jsonBody = """{"name": "Batman"}"""
    val result = for {
      json <- com.github.agourlay.cornichon.json.CornichonJson.parseDslJson(jsonBody)
      path <- JsonPath.parse("$.does.not.exist")
      value <- path.runStrict(json)
    } yield value
    result.isLeft
  }

  property("fillInSessionWithResponse stores status, body, and headers") = {
    import scala.collection.immutable.ArraySeq
    val response = HttpResponse(200, ArraySeq("Content-Type" -> "application/json"), """{"ok": true}""")
    val result = HttpService.fillInSessionWithResponse(response, Session.newEmpty, NoOpExtraction, "GET /test")
    result.isRight &&
    result.flatMap(_.get("last-response-status")) == Right("200") &&
    result.flatMap(_.get("last-response-body")) == Right("""{"ok": true}""") &&
    result.flatMap(_.get("last-response-request")) == Right("GET /test")
  }

}
