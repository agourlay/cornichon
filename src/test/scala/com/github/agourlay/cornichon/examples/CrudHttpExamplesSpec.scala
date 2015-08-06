package com.github.agourlay.cornichon.examples

import akka.http.scaladsl.model.StatusCodes._
import com.github.agourlay.cornichon.ExampleServer
import com.github.agourlay.cornichon.core.CornichonFeature
import spray.json.DefaultJsonProtocol._
import spray.json._
import spray.json.lenses.JsonLenses._

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration._

class CrudHttpExamplesSpec extends CornichonFeature with ExampleServer {

  val baseUrl = s"http://localhost:$port"

  // Mandatory feature name
  lazy val featureName = "CRUD HTTP Example"

  // Mandatory request Timeout duration
  lazy val requestTimeout: FiniteDuration = 2000 millis

  // Mandatory Scenarios definition
  lazy val scenarios = Seq(
    scenario("CRUD Superheroes")(

      // Simple GET
      When(GET(s"$baseUrl/superheroes/Batman")),

      // Test status of previous request
      Then(status_is(200)),

      // Or provide predicate for response status
      When(GET(s"$baseUrl/superheroes/Batman", expectedStatusCode = OK)),

      // Query Batman
      When(GET(s"$baseUrl/superheroes/Batman", expectedBody =
        """
         {
           "name": "Batman",
           "realName": "Bruce Wayne",
           "city": "Gotham city",
           "publisher": "DC"
         }
        """.parseJson
      )),

      // Assert body using any function, here e.g with Spray lens
      When(GET(s"$baseUrl/superheroes/Batman", _.body.extract[String]('city), "Gotham city")),

      // Let's try some 404's

      // Provide predicate for response status
      When(GET(s"$baseUrl/superheroes/Scalaman", expectedStatusCode = NotFound)),

      // Or provide predicate for response body as JsValue
      When(GET(s"$baseUrl/superheroes/Scalaman", expectedBody =
        """
          {
            "error": "Superhero Scalaman not found"
          }
        """.parseJson
      )),

      // Extract from body using lense
      When(GET(s"$baseUrl/superheroes/Scalaman", _.body.extract[String]('error), "Superhero Scalaman not found")),

      // Create Scalaman!
      When(POST(s"$baseUrl/superheroes",
        payload =
          """
            {
              "name": "Scalaman",
              "realName": "Oleg Ilyenko",
              "city": "Berlin",
              "publisher": "DC"
            }
          """.parseJson)),

      // Status Created
      Then(status_is(201)),

      // Query Scalaman
      When(GET(s"$baseUrl/superheroes/Scalaman", expectedBody =
        """
         {
          "name": "Scalaman",
          "realName": "Oleg Ilyenko",
          "city": "Berlin",
          "publisher": "DC"
         }
        """.parseJson
      )),

      // Update Scalaman
      When(PUT(s"$baseUrl/superheroes",
        payload =
          """
            {
              "name": "Scalaman",
              "realName": "Oleg Ilyenko",
              "city": "Pankow",
              "publisher": "DC"
            }
          """.parseJson)),

      // Status updated
      Then(status_is(200)),

      // Query updated Scalaman
      When(GET(s"$baseUrl/superheroes/Scalaman", expectedBody =
        """
         {
          "name": "Scalaman",
          "realName": "Oleg Ilyenko",
          "city": "Pankow",
          "publisher": "DC"
         }
        """.parseJson
      )),

      // Let's delete someone we don't like
      When(GET(s"$baseUrl/superheroes/GreenLantern", expectedStatusCode = OK)),

      When(DELETE(s"$baseUrl/superheroes/GreenLantern")),

      When(GET(s"$baseUrl/superheroes/GreenLantern", expectedStatusCode = NotFound)))
  )
}