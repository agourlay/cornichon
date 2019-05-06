package com.github.agourlay.cornichon.http

import com.github.agourlay.cornichon.core.{ Scenario, Session }
import com.github.agourlay.cornichon.http.HttpService._
import com.github.agourlay.cornichon.http.steps.HeadersSteps.HeadersStepBuilder
import com.github.agourlay.cornichon.steps.StepUtilSpec
import io.circe.testing.ArbitraryInstances
import org.scalatest.{ AsyncWordSpec, Matchers, OptionValues }
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks

class HeaderStepsSpec extends AsyncWordSpec
  with ScalaCheckPropertyChecks
  with ArbitraryInstances
  with Matchers
  with OptionValues
  with StepUtilSpec {

  private def addHeaderToSession(s: Session)(name: String, value: String) =
    s.addValue(SessionKeys.lastResponseHeadersKey, encodeSessionHeader(name, value)).valueUnsafe

  "HeaderSteps" when {
    "HeadersNameStepBuilder" must {
      "is present" in {
        val session = addHeaderToSession(Session.newEmpty)("test-key", "test")
        val step = HeadersStepBuilder.name("test-key").isPresent
        val s = Scenario("scenario with HeaderSteps", step :: Nil)
        engine.runScenario(session)(s).map { r ⇒
          r.isSuccess should be(true)
        }
      }

      "is present (case-insensitive)" in {
        val session = addHeaderToSession(Session.newEmpty)("test-Key", "test")
        val step = HeadersStepBuilder.name("test-key").isPresent
        val s = Scenario("scenario with HeaderSteps", step :: Nil)
        engine.runScenario(session)(s).map { r ⇒
          r.isSuccess should be(true)
        }
      }

      "is absent" in {
        val session = addHeaderToSession(Session.newEmpty)("test-key", "test")
        val step = HeadersStepBuilder.name("test-key2").isAbsent
        val s = Scenario("scenario with HeaderSteps", step :: Nil)
        engine.runScenario(session)(s).map { r ⇒
          r.isSuccess should be(true)
        }
      }
    }

    "HeaderStepBuilder" must {
      "is" in {
        val session = addHeaderToSession(Session.newEmpty)("test-key", "test")
        val step = HeadersStepBuilder.is("test-key" -> "test")
        val s = Scenario("scenario with HeaderSteps", step :: Nil)
        engine.runScenario(session)(s).map { r ⇒
          r.isSuccess should be(true)
        }
      }

      "is (case-insensitive)" in {
        val session = addHeaderToSession(Session.newEmpty)("test-Key", "test")
        val step = HeadersStepBuilder.is("test-key" -> "test")
        val s = Scenario("scenario with HeaderSteps", step :: Nil)
        engine.runScenario(session)(s).map { r ⇒
          r.isSuccess should be(true)
        }
      }

      "contain" in {
        val session = addHeaderToSession(Session.newEmpty)("test-key", "test")
        val step = HeadersStepBuilder.contain("test-key" -> "test")
        val s = Scenario("scenario with HeaderSteps", step :: Nil)
        engine.runScenario(session)(s).map { r ⇒
          r.isSuccess should be(true)
        }
      }

      "contain (case-insensitive)" in {
        val session = addHeaderToSession(Session.newEmpty)("test-Key", "test")
        val step = HeadersStepBuilder.contain("test-key" -> "test")
        val s = Scenario("scenario with HeaderSteps", step :: Nil)
        engine.runScenario(session)(s).map { r ⇒
          r.isSuccess should be(true)
        }
      }
    }
  }
}
