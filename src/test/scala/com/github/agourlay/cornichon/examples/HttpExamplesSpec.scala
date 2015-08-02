package com.github.agourlay.cornichon.examples

import akka.http.scaladsl.model.StatusCodes._
import com.github.agourlay.cornichon.core.CornichonFeature
import spray.json.DefaultJsonProtocol._
import spray.json._
import spray.json.lenses.JsonLenses._

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration._

class HttpExamplesSpec extends CornichonFeature with ExampleServer {

  // use to start Superheroes exampleAPI
  lazy val port = 8080
  val baseUrl = s"http://localhost:$port"

  // Mandatory feature name
  lazy val featureName = "HTTP DSL"

  // Mandatory request Timeout duration
  lazy val requestTimeout: FiniteDuration = 2000 millis

  // Mandatory Scenarios definition
  lazy val scenarios = Seq(
    scenario("Playing with the http DSL")(

      // Simple GET
      When(GET(s"$baseUrl/superheroes/Batman")),

      // Test status of previous request
      Then(status_is(200)),

      // Or provide predicate for response status
      When(GET(s"$baseUrl/superheroes/Batman", _.status == OK)),

      // Test body of previous request body as String
      Then(response_body_is(
        """
          |{
          |  "name": "Batman",
          |  "realName": "Bruce Wayne",
          |  "city": "Gotham city",
          |  "publisher": "DC"
          |}
        """.stripMargin.trim)
      ),

      // Or provide predicate for response body as JsValue
      When(GET(s"$baseUrl/superheroes/Batman", _.body ==
        """
          |{
          |  "name": "Batman",
          |  "realName": "Bruce Wayne",
          |  "city": "Gotham city",
          |  "publisher": "DC"
          |}
        """.stripMargin.parseJson
      )),

      // Extract from body using lense
      When(GET(s"$baseUrl/superheroes/Batman", _.body.extract[String]('city) == "Gotham city")),

      // It is just a function ;)
      When(GET(s"$baseUrl/superheroes/Batman", r ⇒ r.status == OK &&
        r.body == """
          |{
          |  "name": "Batman",
          |  "realName": "Bruce Wayne",
          |  "city": "Gotham city",
          |  "publisher": "DC"
          |}
        """.stripMargin.parseJson
      )),

      // Let's try some 404's

      // Simple GET
      When(GET(s"$baseUrl/superheroes/Scalaman")),

      // Test status of previous request
      Then(status_is(404)),

      // Provide predicate for response status
      When(GET(s"$baseUrl/superheroes/Scalaman", _.status == NotFound)),

      // Or provide predicate for response body as JsValue
      When(GET(s"$baseUrl/superheroes/Scalaman", _.body ==
        """
          |{
          |  "error": "Superhero Scalaman not found"
          |}
        """.stripMargin.parseJson
      )),

      // Extract from body using lense
      When(GET(s"$baseUrl/superheroes/Scalaman", _.body.extract[String]('error) == "Superhero Scalaman not found")),

      // Let's play with the session

      // Debug steps printing into console
      Then(showSession),
      Then(showLastStatus),
      Then(showLastReponseJson),

      // Set a key/value in the Scenario's session
      Given(Set("favorite-superhero", "Batman")),

      // Retrieve dynamically from session with <key>
      When(GET(s"$baseUrl/superheroes/<favorite-superhero>", _.body ==
        """
          |{
          |  "name": "Batman",
          |  "realName": "Bruce Wayne",
          |  "city": "Gotham city",
          |  "publisher": "DC"
          |}
        """.stripMargin.parseJson
      ))
    )
  )
}