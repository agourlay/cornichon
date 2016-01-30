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

        Then assert status.is(200)

        And assert body.is(
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

        And assert body.ignoring(root.city, root.publisher).is(
          """
          {
            "name": "Batman",
            "realName": "Bruce Wayne",
            "hasSuperpowers": false
          }
          """
        )

        // Support for GraphQL JSON input for lightweight definition
        // Requires the import of com.github.agourlay.cornichon.json.CornichonJson._
        And assert body.ignoring(root.city, root.publisher).is(
          gql"""
          {
            name: "Batman",
            realName: "Bruce Wayne",
            hasSuperpowers: false
          }
          """
        )

        And assert body.ignoring(root.publisher.name, root.publisher.location).is(
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
          """
        )

        // Compare only against provided keys
        And assert body.withWhiteList.is(
          """
          {
            "name": "Batman",
            "realName": "Bruce Wayne"
          }
          """
        )

        // Test part of response body by providing a JsonPath
        Then assert body.path(root.city).is("Gotham city")

        Then assert body.path(root.hasSuperpowers).is(false)

        Then assert body.path(root.publisher).is(
          """
          {
            "name":"DC",
            "foundationYear":1934,
            "location":"Burbank, California"
          } """
        )

        Then assert body.path(root.publisher).ignoring(root.location).is(
          """
          {
            "name":"DC",
            "foundationYear":1934
          } """
        )

        Then assert body.path(root.publisher.name).is("DC")

        Then assert body.path(root.publisher.foundationYear).is(1934)

        When I GET("/superheroes/Scalaman")

        Then assert status.is(404)

        And assert body.is(
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

        Then assert status.is(401)

        Then assert body.is("The resource requires authentication, which was not supplied with the request")

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

        Then assert status.is(201)

        When I GET("/superheroes/Scalaman")

        Then assert body.ignoring(root.publisher, root.hasSuperpowers, root.city).is(
          """
          {
            "name": "Scalaman",
            "realName": "Oleg Ilyenko"
          }
          """
        )

        When I GET("/superheroes/Scalaman", params = "protectIdentity" → "true")

        Then assert body.ignoring(root.publisher).is(
          """
          {
            "name": "Scalaman",
            "realName": "XXXXX",
            "hasSuperpowers": false,
            "city": "Berlin"
          }
          """
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

          Then assert headers.contains("Content-Encoding" → "gzip")

          Then assert body.path(root.city).is("Pankow")
        }

        Then assert status.is(200)

        Then assert body.ignoring(root.publisher).is(
          """
          {
            "name": "Scalaman",
            "realName": "Oleg Ilyenko",
            "hasSuperpowers": true,
            "city": "Pankow"
          }
          """
        )

        When I GET("/superheroes/GreenLantern")

        Then assert status.is(200)

        When I DELETE("/superheroes/GreenLantern")

        When I GET("/superheroes/GreenLantern")

        Then assert status.is(404)
      }

      Scenario("demonstrate collection features") {

        When I GET("/superheroes")

        Then assert bodyArray.inOrder.ignoring(root.publisher).is(
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
          }]"""
        )

        Then assert bodyArray.inOrder.ignoring(root.publisher).is(
          """
          |      name      |    realName    |     city      |  hasSuperpowers |
          |    "Batman"    | "Bruce Wayne"  | "Gotham city" |      false      |
          |   "Superman"   | "Clark Kent"   | "Metropolis"  |      true       |
          | "GreenLantern" | "Hal Jordan"   | "Coast City"  |      true       |
          |   "Spiderman"  | "Peter Parker" | "New York"    |      true       |
          |    "IronMan"   | "Tony Stark"   | "New York"    |      false      |
        """
        )

        Then assert bodyArray.ignoring(root.hasSuperpowers, root.publisher).is(
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
          }]"""
        )

        Then assert bodyArray.sizeIs(5)

        And assert bodyArray.contains(
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

        Then assert status.is(200)

        And I GET("/superheroes")

        Then assert bodyArray.sizeIs(4)

        And assert_not bodyArray.contains(
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

        Then assert body.ignoring(root.hasSuperpowers, root.publisher).is(
          """
          {
            "name": "Batman",
            "realName": "Bruce Wayne",
            "city": "Gotham city"
          }
          """
        )

        // Set a key/value in the Scenario's session
        And I save("favorite-superhero" → "Batman")

        Then assert session_contains("favorite-superhero" → "Batman")

        // Retrieve dynamically from session with <key> for URL construction
        When I GET("/superheroes/<favorite-superhero>")

        Then assert body.ignoring("hasSuperpowers", "publisher").is(
          """
          {
            "name": "<favorite-superhero>",
            "realName": "Bruce Wayne",
            "city": "Gotham city"
          }
          """
        )

        // Extract value from response into session for reuse
        And I save_body_key(
          "city" → "batman-city",
          "realName" → "batman-real-name"
        )

        // Or with extractor
        And I save_body_key("city" → "batman-city")

        Then assert session_contains("batman-city" → "Gotham city")

        Then assert session_value("batman-city").is("Gotham city")

        And I show_last_response_body_as_json

        Then assert body.ignoring(root.hasSuperpowers, root.publisher).is(
          """
          {
            "name": "<favorite-superhero>",
            "realName": "Bruce Wayne",
            "city": "<batman-city>"
          }
          """
        )

        Then assert headers.contains("Server" → "akka-http/2.3.12")

        // To make debugging easier, here are some debug steps printing into console
        And I show_session
        And I show_last_status
        And I show_last_response_body
        And I show_last_response_headers
      }

      Scenario("demonstrate wrapping DSL blocks") {

        When I GET("/superheroes/Batman")

        // Using registered extractor at the bottom
        Then assert body.path(root.name).is("<name>")

        // Repeat series of Steps
        Repeat(3) {
          When I GET("/superheroes/Batman")

          Then assert status.is(200)
        }

        // Nested Repeats
        Repeat(3) {

          When I GET("/superheroes/Superman")

          Then assert status.is(200)

          Repeat(2) {

            When I GET("/superheroes/Batman")

            Then assert status.is(200)
          }
        }

        And I show_last_status

        // Execute steps in parallel 'factor times'
        Concurrently(factor = 3, maxTime = 20 seconds) {

          When I GET("/superheroes/Batman")

          Then assert status.is(200)
        }

        // Repeat series of Steps until it succeed
        Eventually(maxDuration = 10 seconds, interval = 200 milliseconds) {

          When I GET("/superheroes/random")

          Then assert body.ignoring(root.hasSuperpowers, root.publisher).is(
            """
            {
              "name": "Batman",
              "realName": "Bruce Wayne",
              "city": "Gotham city"
            }
            """
          )
        }

        // Blocks can be nested
        Concurrently(factor = 2, maxTime = 20 seconds) {

          Eventually(maxDuration = 10 seconds, interval = 200 milliseconds) {

            When I GET("/superheroes/random")

            Then assert body.ignoring(root.hasSuperpowers, root.publisher).is(
              """
              {
                "name": "Batman",
                "realName": "Bruce Wayne",
                "city": "Gotham city"
              }
              """
            )
          }
        }
      }

      Scenario("demonstrate streaming support") {

        Given I wait(1 second)

        // SSE streams are aggregated over a period of time in an Array, the array predicate can be reused :)
        When I GET_SSE("/stream/superheroes", takeWithin = 2 second, params = "justName" → "true")

        Then assert bodyArray.sizeIs(5)

        Then assert body.is(
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