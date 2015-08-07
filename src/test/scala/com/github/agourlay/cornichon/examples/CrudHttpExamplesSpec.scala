package com.github.agourlay.cornichon.examples

import com.github.agourlay.cornichon.ExampleServer
import com.github.agourlay.cornichon.core.CornichonFeature
import spray.json._

class CrudHttpExamplesSpec extends CornichonFeature with ExampleServer {

  lazy val feat =
    feature("CRUD HTTP Example") {
      scenario("CRUD Superheroes")(

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

        Then assert status_is(404)
      )
    }
}