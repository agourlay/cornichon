package com.github.agourlay.cornichon.examples

import akka.http.scaladsl.model.StatusCodes._
import com.github.agourlay.cornichon.core.CornichonFeature
import spray.json.DefaultJsonProtocol._
import spray.json.lenses.JsonLenses._

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration._

class LowLevelScalaExamplesSpec extends CornichonFeature with ExampleServer {

  val baseUrl = s"http://localhost:$port"

  lazy implicit val requestTimeout: FiniteDuration = 2000 millis

  lazy val featureName = "Low level Scala Dsl test"

  lazy val scenarios = Seq(
    scenario("test scenario")(
      Given("A value") { s ⇒
        val x = 33
        val s2 = s.addValue("my-key", "crazy value")
        val s3 = s2.addValue("name-in-title", "String")
        (x, s3)
      }(_ > 0),
      When("I take a letter <name-in-title>") { s ⇒
        val x = 'A'
        (x, s)
      }(_ != 'Z'),
      When("I check the session") { s ⇒
        val x = s.getKey("my-key")
        (x, s)
      }(_.contains("crazy value")),
      Then("The result should be") { s ⇒
        val x = "Batman!"
        val s1 = s.addValue("test-url", s"$baseUrl/superheroes/Batman")
        (x, s1)
      }(_.endsWith("!")),
      Then("Checking status of call to <test-url> ") { s ⇒
        val x = Get("<test-url>")(s)
        (x, s)
      }(r ⇒ Xor2Predicate(r)(_.status == OK)),
      Then("When I contact <test-url>") { s ⇒
        val x = Get("<test-url>")(s)
        (x, s)
      }(r ⇒ Xor2Predicate(r)(_.body.extract[String]('name) == "Batman"))
    )
  )
}
