package com.github.agourlay.cornichon.examples

import com.github.agourlay.cornichon.ExampleServer
import com.github.agourlay.cornichon.core.CornichonFeature

import scala.collection.immutable.HashMap
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration._

import spray.json._

class HttpParamsExamplesSpec extends CornichonFeature with ExampleServer {

  val baseUrl = s"http://localhost:$port"

  // Mandatory feature name
  lazy val featureName = "HTTP params Example"

  // Mandatory request Timeout duration
  lazy val requestTimeout: FiniteDuration = 2000 millis

  // Mandatory Scenarios definition
  lazy val scenarios = Seq(
    scenario("Collection Superheroes with params")(

      // Simple GET
      When(GET(s"$baseUrl/superheroes", expectedBody = """
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
  )

}
