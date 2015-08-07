package com.github.agourlay.cornichon.examples

import com.github.agourlay.cornichon.ExampleServer
import com.github.agourlay.cornichon.core.CornichonFeature

import scala.collection.immutable.HashMap

import spray.json._

class HttpParamsExamplesSpec extends CornichonFeature with ExampleServer {

  lazy val feat =
    feature("HTTP params Example") {
      scenario("Collection Superheroes with params")(

        // Simple GET
        When(GET(s"$baseUrl/superheroes", expectedBody =
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
        )),

        When(GET(s"$baseUrl/superheroes", withParams = HashMap(
          "key1" -> "value1",
          "key2" -> "value2",
          "key3" -> "value3")))
      )
    }
}
