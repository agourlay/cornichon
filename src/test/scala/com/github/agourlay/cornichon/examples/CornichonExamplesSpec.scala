package com.github.agourlay.cornichon.examples

import com.github.agourlay.cornichon.ExampleServer
import com.github.agourlay.cornichon.core.CornichonFeature
import spray.json._
import spray.json.lenses.JsonLenses._
import spray.json.DefaultJsonProtocol._

class CornichonExamplesSpec extends CornichonFeature with ExampleServer {

  lazy val feat =
    Feature("Cornichon feature Example")(

      Scenario("CRUD Feature demo")(

        When I GET(s"$baseUrl/superheroes/Batman"),

        Then assert status_is(200),

        And assert response_body_is(
          """
          {
            "name": "Batman",
            "realName": "Bruce Wayne",
            "city": "Gotham city",
            "publisher": "DC"
          }
          """.parseJson),

        And assert response_body_is(
          """
          {
            "name": "Batman",
            "realName": "Bruce Wayne"
          }
          """.parseJson, ignoredKeys = "publisher", "city"),

        // Test response body as a String by providing a transformation fct (here using spray json-lenses)
        Then assert response_body_is(_.extract[String]('city), "Gotham city"),

        Then assert headers_contain("Server" -> "akka-http/2.3.12"),

        When I GET(s"$baseUrl/superheroes/Scalaman"),

        Then assert status_is(404),

        And assert response_body_is(
          """
          {
            "error": "Superhero Scalaman not found"
          }
          """.parseJson
        ),

        When I POST(s"$baseUrl/superheroes", payload =
          """
          {
            "name": "Scalaman",
            "realName": "Oleg Ilyenko",
            "city": "Berlin",
            "publisher": "DC"
          }
          """.parseJson),

        Then assert status_is(201),

        When I GET(s"$baseUrl/superheroes/Scalaman"),

        Then assert response_body_is(
          """
          {
            "name": "Scalaman",
            "realName": "Oleg Ilyenko",
            "city": "Berlin",
            "publisher": "DC"
          }
          """.parseJson),

        When I GET(s"$baseUrl/superheroes/Scalaman", params =
          "protectIdentity" -> "true",
          "random-useless-header" -> "test"),

        Then assert response_body_is(
          """
          {
            "name": "Scalaman",
            "realName": "XXXXX",
            "city": "Berlin",
            "publisher": "DC"
          }
          """.parseJson),

        When I PUT(s"$baseUrl/superheroes", payload =
          """
          {
            "name": "Scalaman",
            "realName": "Oleg Ilyenko",
            "city": "Pankow",
            "publisher": "DC"
          }
          """.parseJson),

        Then assert status_is(200),

        Then assert response_body_is(
          """
          {
            "name": "Scalaman",
            "realName": "Oleg Ilyenko",
            "city": "Pankow",
            "publisher": "DC"
          }
          """.parseJson
        ),

        When I GET(s"$baseUrl/superheroes/GreenLantern"),

        Then assert status_is(200),

        When I DELETE(s"$baseUrl/superheroes/GreenLantern"),

        When I GET(s"$baseUrl/superheroes/GreenLantern"),

        Then assert status_is(404)),

      Scenario("Collection Feature demo")(

        // Simple GET
        When I GET(s"$baseUrl/superheroes"),

        Then assert response_body_is(
          """
          [{
            "name": "Batman",
            "realName": "Bruce Wayne",
            "city": "Gotham city",
            "publisher": "DC"
          }, {
            "name": "Superman",
            "realName": "Clark Kent",
            "city": "Metropolis",
            "publisher": "DC"
          }, {
            "name": "Spiderman",
            "realName": "Peter Parker",
            "city": "New York",
            "publisher": "Marvel"
          }, {
            "name": "IronMan",
            "realName": "Tony Stark",
            "city": "New York",
            "publisher": "Marvel"
          },
          {
            "name": "Scalaman",
            "realName": "Oleg Ilyenko",
            "city": "Pankow",
            "publisher": "DC"
          }]""".parseJson
        ),

        Then assert response_body_array_size_is(5),

        And assert response_body_array_contains(
          """
          {
            "name": "IronMan",
            "realName": "Tony Stark",
            "city": "New York",
            "publisher": "Marvel"
          }
          """.parseJson),

        When I DELETE(s"$baseUrl/superheroes/IronMan"),

        Then assert status_is(200),

        And I GET(s"$baseUrl/superheroes"),

        Then assert response_body_array_size_is(4),

        And assert response_body_array_not_contain(
          """
          {
            "name": "IronMan",
            "realName": "Tony Stark",
            "city": "New York",
            "publisher": "Marvel"
          }
          """.parseJson)
      ),

      Scenario("Session feature demo")(

        When I GET(s"$baseUrl/superheroes/Batman"),

        Then assert response_body_is(
          """
          {
            "name": "Batman",
            "realName": "Bruce Wayne",
            "city": "Gotham city",
            "publisher": "DC"
          }
          """.parseJson),

        // Set a key/value in the Scenario's session
        And I SET("favorite-superhero" -> "Batman"),

        // Retrieve dynamically from session with <key> for URL construction
        When I GET(s"$baseUrl/superheroes/<favorite-superhero>"),

        Then assert response_body_is(
          """
          {
            "name": "<favorite-superhero>",
            "realName": "Bruce Wayne",
            "city": "Gotham city",
            "publisher": "DC"
          }
          """.parseJson
        ),

        // To make debugging easier, here are some debug steps printing into console
        And I showSession,
        And I showLastStatus,
        And I showLastResponseJson,
        And I showLastResponseHeaders
      )
    )
}