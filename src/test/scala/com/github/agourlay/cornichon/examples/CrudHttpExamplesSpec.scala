package com.github.agourlay.cornichon.examples

import akka.http.scaladsl.model.StatusCodes._
import com.github.agourlay.cornichon.ExampleServer
import com.github.agourlay.cornichon.core.CornichonFeature
import spray.json.DefaultJsonProtocol._
import spray.json._
import spray.json.lenses.JsonLenses._

class CrudHttpExamplesSpec extends CornichonFeature with ExampleServer {

  lazy val feat =
    feature("CRUD HTTP Example") {
      scenario("CRUD Superheroes")(

        // Simple GET
        When I GET(s"$baseUrl/superheroes/Batman"),

        // Test status of previous request
        Then I status_is(200),

        // Or provide predicate for response status
        When I GET(s"$baseUrl/superheroes/Batman", expectedStatusCode = OK),

        // Query Batman
        When I GET(s"$baseUrl/superheroes/Batman"),

        // Check body of previous request body
        Then assert response_body_is(
          """
          {
            "name": "Batman",
            "realName": "Bruce Wayne",
            "city": "Gotham city",
            "publisher": "DC"
          }
        """.parseJson),

        // Assert body directly using any function, here e.g with Spray lens
        When I GET(s"$baseUrl/superheroes/Batman", _.body.extract[String]('city), "Gotham city"),

        // Let's try some 404's
        // Provide predicate for response status
        When I GET(s"$baseUrl/superheroes/Scalaman", expectedStatusCode = NotFound),

        // Or provide predicate for response body as JsValue
        When I GET(s"$baseUrl/superheroes/Scalaman", expectedBody =
          """
          {
            "error": "Superhero Scalaman not found"
          }
        """.parseJson
        ),

        // Extract from body using lense
        When I GET(s"$baseUrl/superheroes/Scalaman", _.body.extract[String]('error), "Superhero Scalaman not found"),

        // Create Scalaman!
        When I POST(s"$baseUrl/superheroes",
          payload =
            """
            {
              "name": "Scalaman",
              "realName": "Oleg Ilyenko",
              "city": "Berlin",
              "publisher": "DC"
            }
          """.parseJson),

        // Status Created
        Then assert status_is(201),

        // Query Scalaman
        When I GET(s"$baseUrl/superheroes/Scalaman", expectedBody =
          """
         {
          "name": "Scalaman",
          "realName": "Oleg Ilyenko",
          "city": "Berlin",
          "publisher": "DC"
         }
        """.parseJson
        ),

        // Update Scalaman
        When I PUT(s"$baseUrl/superheroes",
          payload =
            """
            {
              "name": "Scalaman",
              "realName": "Oleg Ilyenko",
              "city": "Pankow",
              "publisher": "DC"
            }
          """.parseJson),

        // Status updated
        Then assert status_is(200),

        // Query updated Scalaman
        When I GET(s"$baseUrl/superheroes/Scalaman", expectedBody =
          """
         {
          "name": "Scalaman",
          "realName": "Oleg Ilyenko",
          "city": "Pankow",
          "publisher": "DC"
         }
        """.parseJson
        ),

        // Let's delete someone we don't like
        When I GET(s"$baseUrl/superheroes/GreenLantern", expectedStatusCode = OK),

        When I DELETE(s"$baseUrl/superheroes/GreenLantern"),

        When I GET(s"$baseUrl/superheroes/GreenLantern", expectedStatusCode = NotFound)
      )
    }
}