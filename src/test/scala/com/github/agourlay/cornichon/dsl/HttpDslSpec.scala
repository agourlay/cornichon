package com.github.agourlay.cornichon.dsl

import com.github.agourlay.cornichon.{ ExampleServer, ScenarioUtilSpec }
import com.github.agourlay.cornichon.core.Scenario
import org.scalatest.{ BeforeAndAfterAll, Matchers, WordSpec }
import akka.http.scaladsl.model.StatusCodes._
import spray.json.DefaultJsonProtocol._
import spray.json._
import spray.json.lenses.JsonLenses._

class HttpDslSpec extends WordSpec with Matchers with ScenarioUtilSpec with ExampleServer {

  "HTTP Dsls" must {
    "execute scenarios" in {
      val featureTest = new DslTest {

        val feat = feature("HTTP Dsl") {
          scenario("Playing with the http DSL")(

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

            When I GET(s"$baseUrl/superheroes/Batman", _.status, OK),

            When I GET(s"$baseUrl/superheroes/Batman", _.body,
              """
                {
                  "name": "Batman",
                  "realName": "Bruce Wayne",
                  "city": "Gotham city",
                  "publisher": "DC"
                }
              """.parseJson
            ),

            When I GET(s"$baseUrl/superheroes/Batman", _.body.extract[String]('city), "Gotham city"),

            And I SET("favorite-superhero", "Batman"),

            When I GET(s"$baseUrl/superheroes/<favorite-superhero>", _.body,
              """
              {
                "name": "<favorite-superhero>",
                "realName": "Bruce Wayne",
                "city": "Gotham city",
                "publisher": "DC"
              }
              """.parseJson
            )
          )
        }
      }

      val featureReport = featureTest.runFeature()
      printlnFailedScenario(featureReport)
      featureReport.success should be(true)
    }
  }

}
