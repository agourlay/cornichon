package com.github.agourlay.cornichon.examples

import com.github.agourlay.cornichon.ExampleServer
import com.github.agourlay.cornichon.core.CornichonFeature

import spray.json._

class CollectionHttpExamplesSpec extends CornichonFeature with ExampleServer {

  lazy val feat =
    feature("Collection HTTP Example") {
      scenario("Collection Superheroes")(

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
            "name": "GreenLantern",
            "realName": "Hal Jordan",
            "city": "Coast City",
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
          }]""".parseJson
        ),

        Then assert response_body_array_size_is(5),

        And assert response_body_array_contains(
          """
        {
          "name": "GreenLantern",
          "realName": "Hal Jordan",
          "city": "Coast City",
          "publisher": "DC"
        } """.parseJson),

        When I DELETE(s"$baseUrl/superheroes/GreenLantern"),

        And I GET(s"$baseUrl/superheroes"),

        Then assert response_body_array_size_is(4),

        And assert response_body_array_not_contain(
          """
        {
          "name": "GreenLantern",
          "realName": "Hal Jordan",
          "city": "Coast City",
          "publisher": "DC"
        } """.parseJson)
      )
    }
}
