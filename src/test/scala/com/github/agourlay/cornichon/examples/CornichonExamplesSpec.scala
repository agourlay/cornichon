package com.github.agourlay.cornichon.examples

import java.nio.charset.StandardCharsets
import java.util.Base64

import akka.http.scaladsl.Http.ServerBinding
import com.github.agourlay.cornichon.CornichonFeature
import com.github.agourlay.cornichon.core.JsonMapper
import com.github.agourlay.cornichon.examples.server.RestAPI
import com.github.agourlay.cornichon.http.HttpService
import com.github.agourlay.cornichon.json.CornichonJson._
import scala.concurrent.Await
import scala.concurrent.duration._

class CornichonExamplesSpec extends CornichonFeature {

  lazy val feature =
    Feature("Cornichon feature example") {

      Scenario("demonstrate CRUD features") {

        When I GET("/superheroes/Batman")

        Then assert status(200)

        And assert body(
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

        And assert body(
          """
          {
            "name": "Batman",
            "realName": "Bruce Wayne",
            "hasSuperpowers": false
          }
          """, ignoring = root.city, root.publisher
        )

        // Support for GraphQL JSON input for lightweight definition
        // Requires the import of com.github.agourlay.cornichon.json.CornichonJson._
        And assert body(
          gql"""
          {
            name: "Batman",
            realName: "Bruce Wayne",
            hasSuperpowers: false
          }
          """, ignoring = root.city, root.publisher
        )

        And assert body(
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
          """, ignoring = root.publisher.name, root.publisher.location
        )

        // Compare only against provided keys
        And assert body(whiteList = true, expected =
          """
          {
            "name": "Batman",
            "realName": "Bruce Wayne"
          }
          """)

        // Test part of response body by providing a JsonPath
        Then assert body(root.city, "Gotham city")

        Then assert body(root.hasSuperpowers, false)

        Then assert body(root.publisher, expected =
          """
          {
            "name":"DC",
            "foundationYear":1934,
            "location":"Burbank, California"
          } """)

        Then assert body(root.publisher, expected =
          """
          {
            "name":"DC",
            "foundationYear":1934
          } """, ignoring = root.location)

        Then assert body(root.publisher.name, "DC")

        Then assert body(root.publisher.foundationYear, 1934)

        When I GET("/superheroes/Scalaman")

        Then assert status(404)

        And assert body(
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

        Then assert status(401)

        Then assert body("The resource requires authentication, which was not supplied with the request")

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

        Then assert status(201)

        When I GET("/superheroes/Scalaman")

        Then assert body(
          """
          {
            "name": "Scalaman",
            "realName": "Oleg Ilyenko"
          }
          """, ignoring = root.publisher, root.hasSuperpowers, root.city
        )

        When I GET("/superheroes/Scalaman", params = "protectIdentity" → "true")

        Then assert body(
          """
          {
            "name": "Scalaman",
            "realName": "XXXXX",
            "hasSuperpowers": false,
            "city": "Berlin"
          }
          """, ignoring = root.publisher
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

          Then assert body(root.city, "Pankow")
        }

        Then assert status(200)

        Then assert body(
          """
          {
            "name": "Scalaman",
            "realName": "Oleg Ilyenko",
            "hasSuperpowers": true,
            "city": "Pankow"
          }
          """, ignoring = root.publisher
        )

        When I GET("/superheroes/GreenLantern")

        Then assert status(200)

        When I DELETE("/superheroes/GreenLantern")

        When I GET("/superheroes/GreenLantern")

        Then assert status(404)
      }

      Scenario("demonstrate collection features") {

        When I GET("/superheroes")

        Then assert body(ordered = true, expected =
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
          }]""", ignoring = root.publisher)

        Then assert body(ordered = true, expected =
          """
          |      name      |    realName    |     city      |  hasSuperpowers |
          |    "Batman"    | "Bruce Wayne"  | "Gotham city" |      false      |
          |   "Superman"   | "Clark Kent"   | "Metropolis"  |      true       |
          | "GreenLantern" | "Hal Jordan"   | "Coast City"  |      true       |
          |   "Spiderman"  | "Peter Parker" | "New York"    |      true       |
          |    "IronMan"   | "Tony Stark"   | "New York"    |      false      |
        """,
          ignoring = root.publisher)

        Then assert body(ordered = false, expected =
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
          }]""", ignoring = root.hasSuperpowers, root.publisher)

        Then assert body_array_size(5)

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

        Then assert status(200)

        And I GET("/superheroes")

        Then assert body_array_size(4)

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

        Then assert body(
          """
          {
            "name": "Batman",
            "realName": "Bruce Wayne",
            "city": "Gotham city"
          }
          """, ignoring = root.hasSuperpowers, root.publisher
        )

        // Set a key/value in the Scenario's session
        And I save("favorite-superhero" → "Batman")

        Then assert session_contains("favorite-superhero" → "Batman")

        // Retrieve dynamically from session with <key> for URL construction
        When I GET("/superheroes/<favorite-superhero>")

        Then assert body(
          """
          {
            "name": "<favorite-superhero>",
            "realName": "Bruce Wayne",
            "city": "Gotham city"
          }
          """, ignoring = "hasSuperpowers", "publisher"
        )

        // Extract value from response into session for reuse
        And I save_body_key(
          "city" → "batman-city",
          "realName" → "batman-real-name"
        )

        // Or with extractor
        And I save_body_key("city" → "batman-city")

        Then assert session_contains("batman-city" → "Gotham city")

        Then assert body(
          """
          {
            "name": "<favorite-superhero>",
            "realName": "Bruce Wayne",
            "city": "<batman-city>"
          }
          """, ignoring = root.hasSuperpowers, root.publisher
        )

        Then assert headers_contain("Server" → "akka-http/2.3.12")

        // To make debugging easier, here are some debug steps printing into console
        And I show_session
        And I show_last_status
        And I show_last_response_body
        And I show_last_response_headers
      }

      Scenario("demonstrate wrapping DSL blocks") {

        When I GET("/superheroes/Batman")

        // Using registered extractor at the bottom
        Then assert body(root.name, "<name>")

        // Repeat series of Steps
        Repeat(3) {
          When I GET("/superheroes/Batman")

          Then assert status(200)
        }

        // Nested Repeats
        Repeat(3) {

          When I GET("/superheroes/Superman")

          Then assert status(200)

          Repeat(2) {

            When I GET("/superheroes/Batman")

            Then assert status(200)
          }
        }

        And I show_last_status

        // Execute steps in parallel 'factor times'
        Concurrently(factor = 3, maxTime = 20 seconds) {

          When I GET("/superheroes/Batman")

          Then assert status(200)
        }

        // Repeat series of Steps until it succeed
        Eventually(maxDuration = 10 seconds, interval = 200 milliseconds) {

          When I GET("/superheroes/random")

          Then assert body(
            """
            {
              "name": "Batman",
              "realName": "Bruce Wayne",
              "city": "Gotham city"
            }
            """, ignoring = root.hasSuperpowers, root.publisher
          )
        }

        // Blocks can be nested
        Concurrently(factor = 2, maxTime = 20 seconds) {

          Eventually(maxDuration = 10 seconds, interval = 200 milliseconds) {

            When I GET("/superheroes/random")

            Then assert body(
              """
              {
                "name": "Batman",
                "realName": "Bruce Wayne",
                "city": "Gotham city"
              }
              """, ignoring = root.hasSuperpowers, root.publisher
            )
          }
        }
      }

      Scenario("demonstrate streaming support") {

        Given I wait(1 second)

        // SSE streams are aggregated over a period of time in an Array, the array predicate can be reused :)
        When I GET_SSE("/stream/superheroes", takeWithin = 2 second, params = "justName" → "true")

        Then assert body_array_size(5)

        Then assert body(
          """
              |   eventType      |      data      |
              | "superhero name" |    "Batman"    |
              | "superhero name" |   "Superman"   |
              | "superhero name" | "GreenLantern" |
              | "superhero name" |   "Spiderman"  |
              | "superhero name" |    "IronMan"   |
            """
        )

        // TODO
        //When I GET_WS("/stream/superheroes", takeWithin = 1 second, params = "justName" → "true")

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
    "name" → JsonMapper(HttpService.LastResponseBodyKey, root.name)
  )
}