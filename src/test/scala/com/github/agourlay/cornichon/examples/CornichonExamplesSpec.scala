package com.github.agourlay.cornichon.examples

import java.nio.charset.StandardCharsets
import java.util.Base64

import akka.http.scaladsl.Http.ServerBinding
import com.github.agourlay.cornichon.CornichonFeature
import com.github.agourlay.cornichon.core.JsonMapper
import com.github.agourlay.cornichon.examples.server.RestAPI
import com.github.agourlay.cornichon.http.HttpService
import scala.concurrent.Await
import scala.concurrent.duration._

class CornichonExamplesSpec extends CornichonFeature {

  lazy val feature =
    Feature("Cornichon feature example") {

      Scenario("demonstrate CRUD features") {

        When I GET("/superheroes/Batman")

        Then assert status_is(200)

        And assert body_is(
          """
          {
            "name": "Batman",
            "realName": "Bruce Wayne",
            "city": "Gotham city",
            "hasSuperpowers": false,
            "publisher":{
              "name":"DC",
              "foundationYear":1934,
              "location":"Burbank, California"
            }
          }
          """
        )

        And assert body_is(
          """
          {
            "name": "Batman",
            "realName": "Bruce Wayne",
            "hasSuperpowers": false
          }
          """, ignoring = "city", "publisher"
        )

        And assert body_is(
          """
          {
            "name": "Batman",
            "realName": "Bruce Wayne",
            "city": "Gotham city",
            "hasSuperpowers": false,
            "publisher":{
              "foundationYear":1934
            }
          }
          """, ignoring = "publisher.name", "publisher.location"
        )

        // Compare only against provided keys
        And assert body_is(whiteList = true, expected =
          """
          {
            "name": "Batman",
            "realName": "Bruce Wayne"
          }
          """)

        // Test part of response body by providing an extractor
        Then assert body_json_path_is("city", "Gotham city")

        Then assert body_json_path_is("hasSuperpowers", false)

        Then assert body_json_path_is("publisher", expected =
          """
          {
            "name":"DC",
            "foundationYear":1934,
            "location":"Burbank, California"
          } """)

        Then assert body_json_path_is("publisher", expected =
          """
          {
            "name":"DC",
            "foundationYear":1934
          } """, ignoring = "location")

        Then assert body_json_path_is("publisher.name", "DC")

        Then assert body_json_path_is("publisher.foundationYear", 1934)

        When I GET("/superheroes/Scalaman")

        Then assert status_is(404)

        And assert body_is(
          """
          {
            "error": "Superhero Scalaman not found"
          }
          """
        )

        When I POST("/superheroes", payload =
          """
          {
            "name": "Scalaman",
            "realName": "Oleg Ilyenko",
            "city": "Berlin",
            "hasSuperpowers": false,
            "publisher":{
              "name":"DC",
              "foundationYear":1934,
              "location":"Burbank, California"
            }
          }
          """)

        Then assert status_is(401)

        Then assert body_is("The resource requires authentication, which was not supplied with the request")

        // Try again with authentication
        When I POST("/superheroes", payload =
          """
          {
            "name": "Scalaman",
            "realName": "Oleg Ilyenko",
            "city": "Berlin",
            "hasSuperpowers": false,
            "publisher":{
              "name":"DC",
              "foundationYear":1934,
              "location":"Burbank, California"
            }
          }
          """)(headers = Seq(("Authorization", "Basic " + Base64.getEncoder.encodeToString("admin:cornichon".getBytes(StandardCharsets.UTF_8)))))

        Then assert status_is(201)

        When I GET("/superheroes/Scalaman")

        Then assert body_is(
          """
          {
            "name": "Scalaman",
            "realName": "Oleg Ilyenko"
          }
          """, ignoring = "publisher", "hasSuperpowers", "city"
        )

        When I GET("/superheroes/Scalaman", params = "protectIdentity" → "true")

        Then assert body_is(
          """
          {
            "name": "Scalaman",
            "realName": "XXXXX",
            "hasSuperpowers": false,
            "city": "Berlin"
          }
          """, ignoring = "publisher"
        )

        WithHeaders(("Authorization", "Basic " + Base64.getEncoder.encodeToString("admin:cornichon".getBytes(StandardCharsets.UTF_8)))) {
          When I PUT("/superheroes", payload =
            """
            {
              "name": "Scalaman",
              "realName": "Oleg Ilyenko",
              "city": "Pankow",
              "hasSuperpowers": true,
              "publisher":{
                "name":"DC",
                "foundationYear":1934,
                "location":"Burbank, California"
              }
            }
            """)(headers = Seq(("Accept-Encoding", "gzip")))

          Then assert headers_contain("Content-Encoding" → "gzip")

          Then assert body_json_path_is("city", "Pankow")
        }

        Then assert status_is(200)

        Then assert body_is(
          """
          {
            "name": "Scalaman",
            "realName": "Oleg Ilyenko",
            "hasSuperpowers": true,
            "city": "Pankow"
          }
          """, ignoring = "publisher"
        )

        When I GET("/superheroes/GreenLantern")

        Then assert status_is(200)

        When I DELETE("/superheroes/GreenLantern")

        When I GET("/superheroes/GreenLantern")

        Then assert status_is(404)
      }

      Scenario("demonstrate collection features") {

        When I GET("/superheroes")

        Then assert body_is(ordered = true, expected =
          """
          [{
            "name": "Batman",
            "realName": "Bruce Wayne",
            "hasSuperpowers": false,
            "city": "Gotham city"
          },
          {
            "name": "Superman",
            "realName": "Clark Kent",
            "hasSuperpowers": true,
            "city": "Metropolis"
          },
          {
            "name": "GreenLantern",
            "realName": "Hal Jordan",
            "hasSuperpowers": true,
            "city": "Coast City"
          },
          {
            "name": "Spiderman",
            "realName": "Peter Parker",
            "hasSuperpowers": true,
            "city": "New York"
          },
          {
            "name": "IronMan",
            "realName": "Tony Stark",
            "hasSuperpowers": false,
            "city": "New York"
          }]""", ignoring = "publisher")

        Then assert body_is(ordered = true, expected =
          """
          |      name      |    realName    |     city      |  hasSuperpowers |
          |    "Batman"    | "Bruce Wayne"  | "Gotham city" |      false      |
          |   "Superman"   | "Clark Kent"   | "Metropolis"  |      true       |
          | "GreenLantern" | "Hal Jordan"   | "Coast City"  |      true       |
          |   "Spiderman"  | "Peter Parker" | "New York"    |      true       |
          |    "IronMan"   | "Tony Stark"   | "New York"    |      false      |
        """,
          ignoring = "publisher")

        Then assert body_is(ordered = false, expected =
          """
          [{
            "name": "Superman",
            "realName": "Clark Kent",
            "city": "Metropolis"
          },
          {
            "name": "Spiderman",
            "realName": "Peter Parker",
            "city": "New York"
          },
          {
            "name": "Batman",
            "realName": "Bruce Wayne",
            "city": "Gotham city"
          },
          {
            "name": "IronMan",
            "realName": "Tony Stark",
            "city": "New York"
          },
          {
            "name": "GreenLantern",
            "realName": "Hal Jordan",
            "city": "Coast City"
          }]""", ignoring = "hasSuperpowers", "publisher")

        Then assert body_array_size_is(5)

        And assert body_array_contains(
          """
          {
            "name": "IronMan",
            "realName": "Tony Stark",
            "city": "New York",
            "hasSuperpowers": false,
            "publisher":{
              "name":"Marvel",
              "foundationYear":1939,
              "location":"135 W. 50th Street, New York City"
            }
          }
          """
        )

        When I DELETE("/superheroes/IronMan")

        Then assert status_is(200)

        And I GET("/superheroes")

        Then assert body_array_size_is(4)

        And assert_not body_array_contains(
          """
          {
            "name": "IronMan",
            "realName": "Tony Stark",
            "city": "New York",
            "hasSuperpowers": false,
            "publisher":{
              "name":"Marvel",
              "foundationYear":1939,
              "location":"135 W. 50th Street, New York City"
            }
          }
          """
        )
      }

      Scenario("demonstrate session features") {

        When I GET("/superheroes/Batman")

        Then assert body_is(
          """
          {
            "name": "Batman",
            "realName": "Bruce Wayne",
            "city": "Gotham city"
          }
          """, ignoring = "hasSuperpowers", "publisher"
        )

        // Set a key/value in the Scenario's session
        And I save("favorite-superhero" → "Batman")

        Then assert session_contains("favorite-superhero" → "Batman")

        // Retrieve dynamically from session with <key> for URL construction
        When I GET("/superheroes/<favorite-superhero>")

        Then assert body_is(
          """
          {
            "name": "<favorite-superhero>",
            "realName": "Bruce Wayne",
            "city": "Gotham city"
          }
          """, ignoring = "hasSuperpowers", "publisher"
        )

        // Extract value from response into session for reuse
        And I save_body_keys(
          "city" → "batman-city",
          "realName" → "batman-real-name"
        )

        // Or with extractor
        And I save_from_body("city", "batman-city")

        Then assert session_contains("batman-city" → "Gotham city")

        Then assert body_is(
          """
          {
            "name": "<favorite-superhero>",
            "realName": "Bruce Wayne",
            "city": "<batman-city>"
          }
          """, ignoring = "hasSuperpowers", "publisher"
        )

        Then assert headers_contain("Server" → "akka-http/2.3.12")

        // To make debugging easier, here are some debug steps printing into console
        And I show_session
        And I show_last_status
        And I show_last_response_body
        And I show_last_response_headers
      }

      Scenario("demonstrate advanced features") {

        When I GET("/superheroes/Batman")

        // Using registered extractor at the bottom
        Then assert body_json_path_is("name", "<name>")

        // Repeat series of Steps
        Repeat(3) {
          When I GET("/superheroes/Batman")

          Then assert status_is(200)
        }

        // Nested Repeats
        Repeat(3) {

          When I GET("/superheroes/Superman")

          Then assert status_is(200)

          Repeat(2) {

            When I GET("/superheroes/Batman")

            Then assert status_is(200)
          }
        }

        And I show_last_status

        // Execute steps in parallel 'factor times'
        Concurrently(factor = 3, maxTime = 20 seconds) {

          When I GET("/superheroes/Batman")

          Then assert status_is(200)
        }

        Eventually(maxDuration = 10 seconds, interval = 200 milliseconds) {

          // SSE streams are aggregated over a period of time in an Array, the array predicate can be reused :)
          When I GET_SSE("/stream/superheroes", takeWithin = 1 second, params = "justName" → "true")

          Then assert body_array_size_is(5)

          Then assert body_is(
            """
          |   eventType      |      data      |
          | "superhero name" |    "Batman"    |
          | "superhero name" |   "Superman"   |
          | "superhero name" | "GreenLantern" |
          | "superhero name" |   "Spiderman"  |
          | "superhero name" |    "IronMan"   |
          """
          )
        }
        // Repeat series of Steps until it succeed
        Eventually(maxDuration = 10 seconds, interval = 200 milliseconds) {

          When I GET("/superheroes/random")

          Then assert body_is(
            """
            {
              "name": "Batman",
              "realName": "Bruce Wayne",
              "city": "Gotham city"
            }
            """, ignoring = "hasSuperpowers", "publisher"
          )
        }

        // Blocks can be nested
        Concurrently(factor = 2, maxTime = 20 seconds) {

          Eventually(maxDuration = 10 seconds, interval = 200 milliseconds) {

            When I GET("/superheroes/random")

            Then assert body_is(
              """
              {
                "name": "Batman",
                "realName": "Bruce Wayne",
                "city": "Gotham city"
              }
              """, ignoring = "hasSuperpowers", "publisher"
            )
          }
        }
      }
    }

  lazy val port = 8080

  // Base url used for all HTTP steps
  override lazy val baseUrl = s"http://localhost:$port"

  var server: ServerBinding = _

  // Starts up test server
  beforeFeature {
    server = Await.result(new RestAPI().start(port), 5 second)
  }

  // Stops test server
  afterFeature {
    Await.result(server.unbind(), 5 second)
  }

  // List of Steps to be executed after each scenario
  afterEachScenario {
    Seq(
      GET("/reset")
    )
  }

  override def registerExtractors = Map(
    "name" → JsonMapper(HttpService.LastResponseBodyKey, "name")
  )
}