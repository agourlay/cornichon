package com.github.agourlay.cornichon.dsl

import com.github.agourlay.cornichon.{ ExampleServer, ScenarioUtilSpec }
import org.scalatest.{ Matchers, WordSpec }
import spray.json.DefaultJsonProtocol._
import spray.json._
import spray.json.lenses.JsonLenses._

class HttpDslSpec extends WordSpec with Matchers with ScenarioUtilSpec with ExampleServer {

  "HTTP Dsls" must {
    "execute scenarios" in {
      val featureTest = new DslTest {

        val feat = Feature("HTTP Dsl") {
          Scenario("Playing with the http DSL")(

            When I GET(s"$baseUrl/superheroes/Batman"),

            Then assert status_is(200),

            Then assert response_body_is(
              """
                {
                  "name": "Batman",
                  "realName": "Bruce Wayne",
                  "city": "Gotham city",
                  "publisher": "DC"
                }
              """.parseJson),

            Then assert response_body_is(_.extract[String]('city), "Gotham city"),

            And I save("favorite-superhero" -> "Batman"),

            Then assert response_body_is(
              """
                {
                  "name": "<favorite-superhero>",
                  "realName": "Bruce Wayne",
                  "city": "Gotham city",
                  "publisher": "DC"
                }
              """.parseJson)
          )
        }
      }

      val featureReport = featureTest.runFeature()
      printlnFailedScenario(featureReport)
      featureReport.success should be(true)
    }
  }

}
