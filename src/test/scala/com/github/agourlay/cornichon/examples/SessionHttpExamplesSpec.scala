package com.github.agourlay.cornichon.examples

import akka.http.scaladsl.model.StatusCodes._
import com.github.agourlay.cornichon.ExampleServer
import com.github.agourlay.cornichon.core.CornichonFeature
import spray.json.DefaultJsonProtocol._
import spray.json._
import spray.json.lenses.JsonLenses._

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration._

class SessionHttpExamplesSpec extends CornichonFeature with ExampleServer {

  val baseUrl = s"http://localhost:$port"

  // Mandatory feature name
  lazy val featureName = "MoreHTTP DSL"

  // Mandatory request Timeout duration
  lazy val requestTimeout: FiniteDuration = 2000 millis

  // Mandatory Scenarios definition
  lazy val scenarios = Seq(
    scenario("Playing with the http DSL")(

      // Simple GET
      When(GET(s"$baseUrl/superheroes/Batman")),

      // Test status of previous request
      Then(status_is(200)),

      // Test body of previous request body as String
      Then(response_body_is(
        """
          {
            "name": "Batman",
            "realName": "Bruce Wayne",
            "city": "Gotham city",
            "publisher": "DC"
          }
        """.parseJson)),

      // Set a key/value in the Scenario's session
      Given(Set("favorite-superhero", "Batman")),

      // Retrieve dynamically from session with <key> for URL construction
      When(GET(s"$baseUrl/superheroes/<favorite-superhero>", expectedBody =
        """
          {
            "name": "<favorite-superhero>",
            "realName": "Bruce Wayne",
            "city": "Gotham city",
            "publisher": "DC"
          }
        """.parseJson
      )),

      // To make debugging easier, here are some debug steps printing into console
      Then(showSession),
      Then(showLastStatus),
      Then(showLastResponseJson)
    )
  )
}