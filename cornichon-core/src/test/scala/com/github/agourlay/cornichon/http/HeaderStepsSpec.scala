package com.github.agourlay.cornichon.http

import com.github.agourlay.cornichon.core.{ Scenario, ScenarioRunner, Session }
import com.github.agourlay.cornichon.http.HttpService._
import com.github.agourlay.cornichon.http.steps.HeadersSteps.HeadersStepBuilder
import com.github.agourlay.cornichon.testHelpers.CommonTestSuite
import munit.FunSuite

class HeaderStepsSpec extends FunSuite with CommonTestSuite {

  private def addHeaderToSession(s: Session)(headers: (String, String)*) =
    s.addValue(SessionKeys.lastResponseHeadersKey, encodeSessionHeaders(headers)).valueUnsafe

  test("HeadersNameStepBuilder is present") {
    val session = addHeaderToSession(Session.newEmpty)("test-key" -> "test")
    val step = HeadersStepBuilder.name("test-key").isPresent
    val s = Scenario("scenario with HeaderSteps", step :: Nil)
    val res = awaitIO(ScenarioRunner.runScenario(session)(s))
    assert(res.isSuccess)
  }

  test("HeadersNameStepBuilder is present (case-insensitive)") {
    val session = addHeaderToSession(Session.newEmpty)("test-Key" -> "test")
    val step = HeadersStepBuilder.name("Test-key").isPresent
    val s = Scenario("scenario with HeaderSteps", step :: Nil)
    val res = awaitIO(ScenarioRunner.runScenario(session)(s))
    assert(res.isSuccess)
  }

  test("HeadersNameStepBuilder is absent") {
    val session = addHeaderToSession(Session.newEmpty)("test-key" -> "test")
    val step = HeadersStepBuilder.name("test-key2").isAbsent
    val s = Scenario("scenario with HeaderSteps", step :: Nil)
    val res = awaitIO(ScenarioRunner.runScenario(session)(s))
    assert(res.isSuccess)
  }

  test("HeaderStepBuilder hasSize") {
    val session = addHeaderToSession(Session.newEmpty)("test-key" -> "test", "test-key2" -> "test")
    val step = HeadersStepBuilder.hasSize(2)
    val s = Scenario("scenario with HeaderSteps", step :: Nil)
    val res = awaitIO(ScenarioRunner.runScenario(session)(s))
    assert(res.isSuccess)
  }

  test("HeaderStepBuilder is") {
    val session = addHeaderToSession(Session.newEmpty)("test-key" -> "Test")
    val step = HeadersStepBuilder.is("test-key" -> "Test")
    val s = Scenario("scenario with HeaderSteps", step :: Nil)
    val res = awaitIO(ScenarioRunner.runScenario(session)(s))
    assert(res.isSuccess)
  }

  test("HeaderStepBuilder is (case-insensitive)") {
    val session = addHeaderToSession(Session.newEmpty)("test-Key" -> "test")
    val step = HeadersStepBuilder.is("Test-key" -> "test")
    val s = Scenario("scenario with HeaderSteps", step :: Nil)
    val res = awaitIO(ScenarioRunner.runScenario(session)(s))
    assert(res.isSuccess)
  }

  test("HeaderStepBuilder contain") {
    val session = addHeaderToSession(Session.newEmpty)("test-key" -> "test")
    val step = HeadersStepBuilder.contain("test-key" -> "test")
    val s = Scenario("scenario with HeaderSteps", step :: Nil)
    val res = awaitIO(ScenarioRunner.runScenario(session)(s))
    assert(res.isSuccess)
  }

  test("HeaderStepBuilder contain (case-insensitive)") {
    val session = addHeaderToSession(Session.newEmpty)("test-Key" -> "test")
    val step = HeadersStepBuilder.contain("Test-key" -> "test")
    val s = Scenario("scenario with HeaderSteps", step :: Nil)
    val res = awaitIO(ScenarioRunner.runScenario(session)(s))
    assert(res.isSuccess)
  }
}
