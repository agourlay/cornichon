package com.github.agourlay.cornichon.examples

import com.github.agourlay.cornichon.ExampleServer
import com.github.agourlay.cornichon.core.CornichonFeature

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration._

import spray.json._

class CollectionHttpExamplesSpec extends CornichonFeature with ExampleServer {

  val baseUrl = s"http://localhost:$port"

  // Mandatory feature name
  lazy val featureName = "Collection HTTP Example"

  // Mandatory request Timeout duration
  lazy val requestTimeout: FiniteDuration = 2000 millis

  // Mandatory Scenarios definition
  lazy val scenarios = Seq(
    scenario("Collection Superheroes")(

      // Simple GET
      When(GET(s"$baseUrl/superheroes", _.body == """
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

      // Test nb of elements
      Then(response_body_array_is(_.elements.size == 5)),

      Then(response_body_array_contains("""
        {
          "name": "GreenLantern",
          "realName": "Hal Jordan",
          "city": "Coast City",
          "publisher": "DC"
        } """.parseJson)),

      When(DELETE(s"$baseUrl/superheroes/GreenLantern")),

      When(GET(s"$baseUrl/superheroes")),

      // Shortcut
      Then(response_body_array_size_is(4)),

      Then(response_body_array_is(!_.elements.contains("""
        {
          "name": "GreenLantern",
          "realName": "Hal Jordan",
          "city": "Coast City",
          "publisher": "DC"
        } """.parseJson)))
    )
  )

}
