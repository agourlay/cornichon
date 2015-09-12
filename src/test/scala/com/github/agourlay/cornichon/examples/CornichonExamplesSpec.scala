package com.github.agourlay.cornichon.examples

import akka.http.scaladsl.Http.ServerBinding
import com.github.agourlay.cornichon.CornichonFeature
import com.github.agourlay.cornichon.server.RestAPI
import scala.concurrent.Await
import scala.concurrent.duration._

class CornichonExamplesSpec extends CornichonFeature {

  lazy val port = 8080
  var server: ServerBinding = _

  // Starts up test server
  override def beforeFeature() =
    server = Await.result(new RestAPI().start(port), 5 second)

  // Stops test server
  override def afterFeature() = Await.result(server.unbind(), 5 second)

  lazy val baseUrl = s"http://localhost:$port"

  def feature =
    Feature("Cornichon feature Example")(

      Scenario("CRUD Feature demo") { implicit b ⇒
        When I GET(s"$baseUrl/superheroes/Batman")

        Then assert status_is(200)

        And assert response_is(
          """
          {
            "name": "Batman",
            "realName": "Bruce Wayne",
            "city": "Gotham city",
            "publisher": "DC"
          }
          """
        )

        When I GET(s"$baseUrl/superheroes/Scalaman")

        Then assert status_is(404)

        And assert response_is(
          """
          {
            "error": "Superhero Scalaman not found"
          }
          """
        )

        When I POST(s"$baseUrl/superheroes", payload =
          """
          {
            "name": "Scalaman",
            "realName": "Oleg Ilyenko",
            "city": "Berlin",
            "publisher": "DC"
          }
          """)

        Then assert status_is(201)

        When I GET(s"$baseUrl/superheroes/Scalaman")

        Then assert response_is(
          """
          {
            "name": "Scalaman",
            "realName": "Oleg Ilyenko",
            "city": "Berlin",
            "publisher": "DC"
          }
          """
        )

        When I GET(s"$baseUrl/superheroes/Scalaman", params = "protectIdentity" → "true")

        Then assert response_is(
          """
          {
            "name": "Scalaman",
            "realName": "XXXXX",
            "city": "Berlin",
            "publisher": "DC"
          }
          """
        )

        When I PUT(s"$baseUrl/superheroes", payload =
          """
          {
            "name": "Scalaman",
            "realName": "Oleg Ilyenko",
            "city": "Pankow",
            "publisher": "DC"
          }
          """)

        Then assert status_is(200)

        Then assert response_is(
          """
          {
            "name": "Scalaman",
            "realName": "Oleg Ilyenko",
            "city": "Pankow",
            "publisher": "DC"
          }
          """
        )

        When I GET(s"$baseUrl/superheroes/GreenLantern")

        Then assert status_is(200)

        When I DELETE(s"$baseUrl/superheroes/GreenLantern")

        When I GET(s"$baseUrl/superheroes/GreenLantern")

        Then assert status_is(404)
      },

      Scenario("Collection Feature demo") { implicit b ⇒

        When I GET(s"$baseUrl/superheroes")

        Then assert response_array_is(
          """
          [{
            "name": "Batman",
            "realName": "Bruce Wayne",
            "city": "Gotham city",
            "publisher": "DC"
          },
          {
            "name": "Superman",
            "realName": "Clark Kent",
            "city": "Metropolis",
            "publisher": "DC"
          },
          {
            "name": "Spiderman",
            "realName": "Peter Parker",
            "city": "New York",
            "publisher": "Marvel"
          },
          {
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
          }]"""
        )

        Then assert response_array_is(
          """
            |    name     |    realName    |     city      |  publisher |
            | "Batman"    | "Bruce Wayne"  | "Gotham city" |   "DC"     |
            | "Superman"  | "Clark Kent"   | "Metropolis"  |   "DC"     |
            | "Spiderman" | "Peter Parker" | "New York"    |   "Marvel" |
            | "IronMan"   | "Tony Stark"   | "New York"    |   "Marvel" |
            | "Scalaman"  | "Oleg Ilyenko" | "Pankow"      |   "DC"     |
          """
        )

        Then assert response_array_is(
          """
          [{
            "name": "Superman",
            "realName": "Clark Kent",
            "city": "Metropolis",
            "publisher": "DC"
          },
          {
            "name": "Spiderman",
            "realName": "Peter Parker",
            "city": "New York",
            "publisher": "Marvel"
          },
          {
            "name": "Batman",
            "realName": "Bruce Wayne",
            "city": "Gotham city",
            "publisher": "DC"
          },
          {
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
          }]""", ordered = false
        )

        Then assert response_array_size_is(5)

        And assert response_array_contains(
          """
          {
            "name": "IronMan",
            "realName": "Tony Stark",
            "city": "New York",
            "publisher": "Marvel"
          }
          """
        )

        When I DELETE(s"$baseUrl/superheroes/IronMan")

        Then assert status_is(200)

        And I GET(s"$baseUrl/superheroes")

        Then assert response_array_size_is(4)

        And assert response_array_does_not_contain(
          """
          {
            "name": "IronMan",
            "realName": "Tony Stark",
            "city": "New York",
            "publisher": "Marvel"
          }
          """
        )
      },

      Scenario("Session feature demo") { implicit b ⇒

        When I GET(s"$baseUrl/superheroes/Batman")

        Then assert response_is(
          """
          {
            "name": "Batman",
            "realName": "Bruce Wayne",
            "city": "Gotham city",
            "publisher": "DC"
          }
          """
        )

        // Set a key/value in the Scenario's session
        And I save("favorite-superhero" → "Batman")

        Then assert session_contains("favorite-superhero" → "Batman")

        // Retrieve dynamically from session with <key> for URL construction
        When I GET(s"$baseUrl/superheroes/<favorite-superhero>")

        Then assert response_is(
          """
          {
            "name": "<favorite-superhero>",
            "realName": "Bruce Wayne",
            "city": "Gotham city",
            "publisher": "DC"
          }
          """
        )

        // Extract value from response into session for reuse
        And I extract_from_response("city", "batman-city")

        // Can be done using an extractor for deeper values
        And I extract_from_response(_ \ "city", "batman-city")

        Then assert session_contains("batman-city" → "Gotham city")

        Then assert response_is(
          """
          {
            "name": "<favorite-superhero>",
            "realName": "Bruce Wayne",
            "city": "<batman-city>",
            "publisher": "DC"
          }
          """
        )

        Then assert headers_contain("Server" → "akka-http/2.3.13")

        // To make debugging easier, here are some debug steps printing into console
        And I show_session
        And I show_last_status
        And I show_last_response_json
        And I show_last_response_headers
      },

      Scenario("Advanced feature demo") { implicit b ⇒

        When I GET(s"$baseUrl/superheroes/Batman")

        And assert response_is(
          """
          {
            "name": "Batman",
            "realName": "Bruce Wayne",
            "city": "Gotham city",
            "publisher": "DC"
          }
          """
        )

        // Ignore keys
        And assert response_is(
          """
          {
            "name": "Batman",
            "realName": "Bruce Wayne"
          }
          """, ignoredKeys = "publisher", "city"
        )

        // Compare only against provided keys
        And assert response_is(
          """
          {
            "name": "Batman",
            "realName": "Bruce Wayne"
          }
          """, whiteList = true
        )

        // TODO setup proper schema for superheroes
        //And assert response_against_schema("https://api.sphere.io/documentation/json-schema/inline/payment.schema.json")

        // Test response body as a String by providing an extractor
        Then assert response_is(_ \ "city", "Gotham city")

        // Repeat serie of Steps
        Repeat(3) {
          When I GET(s"$baseUrl/superheroes/Batman")

          Then assert status_is(200)
        }

        // Nested Repeats
        Repeat(3) {
          When I GET(s"$baseUrl/superheroes/Superman")

          Then assert status_is(200)

          Repeat(2) {
            When I GET(s"$baseUrl/superheroes/Batman")

            Then assert status_is(200)
          }
        }

        // SSE streams are aggregated over a period of time in an Array, the array predicate can be reused :)
        When I GET_SSE(s"$baseUrl/stream/superheroes", takeWithin = 1.seconds, params = "justName" → "true")

        Then assert response_array_size_is(4)

        Then assert response_array_is(
          """
            |   eventType      |    data     |
            | "superhero name" |  "Batman"   |
            | "superhero name" | "Superman"  |
            | "superhero name" | "Spiderman" |
            | "superhero name" | "Scalaman"  |
          """
        )

        // Repeat series of Steps until it succeed
        Eventually(maxDuration = 15.seconds, interval = 200.milliseconds) {
          When I GET(s"$baseUrl/superheroes/random")

          Then assert response_is(
            """
          {
            "name": "Batman",
            "realName": "Bruce Wayne",
            "city": "Gotham city",
            "publisher": "DC"
          }
          """
          )
        }
      }
    )
}