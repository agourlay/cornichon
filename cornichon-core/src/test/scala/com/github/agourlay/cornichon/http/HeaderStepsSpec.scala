package com.github.agourlay.cornichon.http

import com.github.agourlay.cornichon.core.{ Scenario, ScenarioRunner, Session }
import com.github.agourlay.cornichon.http.HttpService._
import com.github.agourlay.cornichon.http.steps.HeadersSteps.HeadersStepBuilder
import com.github.agourlay.cornichon.steps.StepUtilSpec
import io.circe.testing.ArbitraryInstances
import org.scalatest.{ AsyncWordSpec, Matchers, OptionValues }

class HeaderStepsSpec extends AsyncWordSpec
  with ArbitraryInstances
  with Matchers
  with OptionValues
  with StepUtilSpec {

  private def addHeaderToSession(s: Session)(headers: (String, String)*) =
    s.addValue(SessionKeys.lastResponseHeadersKey, encodeSessionHeaders(headers)).valueUnsafe

  "HeaderSteps" when {
    "HeadersNameStepBuilder" must {
      "is present" in {
        val session = addHeaderToSession(Session.newEmpty)("test-key" -> "test")
        val step = HeadersStepBuilder.name("test-key").isPresent
        val s = Scenario("scenario with HeaderSteps", step :: Nil)
        ScenarioRunner.runScenario(session)(s).map { r ⇒
          r.isSuccess should be(true)
        }
      }

      "is present (case-insensitive)" in {
        val session = addHeaderToSession(Session.newEmpty)("test-Key" -> "test")
        val step = HeadersStepBuilder.name("test-key").isPresent
        val s = Scenario("scenario with HeaderSteps", step :: Nil)
        ScenarioRunner.runScenario(session)(s).map { r ⇒
          r.isSuccess should be(true)
        }
      }

      "is absent" in {
        val session = addHeaderToSession(Session.newEmpty)("test-key" -> "test")
        val step = HeadersStepBuilder.name("test-key2").isAbsent
        val s = Scenario("scenario with HeaderSteps", step :: Nil)
        ScenarioRunner.runScenario(session)(s).map { r ⇒
          r.isSuccess should be(true)
        }
      }
    }

    "HeaderStepBuilder" must {

      "hasSize" in {
        val session = addHeaderToSession(Session.newEmpty)("test-key" -> "test", "test-key2" -> "test")
        val step = HeadersStepBuilder.hasSize(2)
        val s = Scenario("scenario with HeaderSteps", step :: Nil)
        ScenarioRunner.runScenario(session)(s).map { r ⇒
          r.isSuccess should be(true)
        }
      }

      "is" in {
        val session = addHeaderToSession(Session.newEmpty)("test-key" -> "Test")
        val step = HeadersStepBuilder.is("test-key" -> "Test")
        val s = Scenario("scenario with HeaderSteps", step :: Nil)
        ScenarioRunner.runScenario(session)(s).map { r ⇒
          r.isSuccess should be(true)
        }
      }

      "is (case-insensitive)" in {
        val session = addHeaderToSession(Session.newEmpty)("test-Key" -> "Test")
        val step = HeadersStepBuilder.is("test-key" -> "Test")
        val s = Scenario("scenario with HeaderSteps", step :: Nil)
        ScenarioRunner.runScenario(session)(s).map { r ⇒
          r.isSuccess should be(true)
        }
      }

      "contain" in {
        val session = addHeaderToSession(Session.newEmpty)("test-key" -> "test")
        val step = HeadersStepBuilder.contain("test-key" -> "test")
        val s = Scenario("scenario with HeaderSteps", step :: Nil)
        ScenarioRunner.runScenario(session)(s).map { r ⇒
          r.isSuccess should be(true)
        }
      }

      "contain (case-insensitive)" in {
        val session = addHeaderToSession(Session.newEmpty)("test-Key" -> "Test")
        val step = HeadersStepBuilder.contain("test-key" -> "Test")
        val s = Scenario("scenario with HeaderSteps", step :: Nil)
        ScenarioRunner.runScenario(session)(s).map { r ⇒
          r.isSuccess should be(true)
        }
      }
    }
  }
}
