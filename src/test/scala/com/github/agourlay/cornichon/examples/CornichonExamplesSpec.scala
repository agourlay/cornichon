package com.github.agourlay.cornichon.examples

import java.nio.charset.StandardCharsets
import java.util.Base64

import akka.http.scaladsl.Http.ServerBinding
import com.github.agourlay.cornichon.CornichonFeature
import com.github.agourlay.cornichon.server.RestAPI
import scala.concurrent.Await
import scala.concurrent.duration._

class CornichonExamplesSpec extends CornichonFeature {

  val port = 8080
  var server: ServerBinding = _

  // Starts up test server
  override def beforeFeature() =
    server = Await.result(new RestAPI().start(port), 5 second)

  // Stops test server
  override def afterFeature() = Await.result(server.unbind(), 5 second)

  val baseUrl = s"http://localhost:$port"

  def feature =
    Feature("Cornichon feature Example")(

      Scenario("CRUD Feature demo") { implicit b ⇒

        When I GET(s"$baseUrl/superheroes/Batman")

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

        // Compare only against provided keys
        And assert body_is(whiteList = true, expected = """
          {
            "name": "Batman",
            "realName": "Bruce Wayne"
          }
          """)

        // Test part of response body by providing an extractor
        Then assert body_is(_ \ "city", "Gotham city")

        Then assert body_is(_ \ "hasSuperpowers", false)

        Then assert body_is(_ \ "publisher", expected = """
          {
            "name":"DC",
            "foundationYear":1934,
            "location":"Burbank, California"
          } """)

        Then assert body_is(_ \ "publisher" \ "name", "DC")

        Then assert body_is(_ \ "publisher" \ "foundationYear", 1934)

        When I GET(s"$baseUrl/superheroes/Scalaman")

        Then assert status_is(404)

        And assert body_is(
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
        When I POST(s"$baseUrl/superheroes", payload =
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

        When I GET(s"$baseUrl/superheroes/Scalaman")

        Then assert body_is(
          """
          {
            "name": "Scalaman",
            "realName": "Oleg Ilyenko"
          }
          """, ignoring = "publisher", "hasSuperpowers", "city"
        )

        When I GET(s"$baseUrl/superheroes/Scalaman", params = "protectIdentity" → "true")

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
          When I PUT(s"$baseUrl/superheroes", payload =
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
            """)
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

        When I GET(s"$baseUrl/superheroes/GreenLantern")

        Then assert status_is(200)

        When I DELETE(s"$baseUrl/superheroes/GreenLantern")

        When I GET(s"$baseUrl/superheroes/GreenLantern")

        Then assert status_is(404)
      },

      Scenario("Collection Feature demo") { implicit b ⇒
        When I GET(s"$baseUrl/superheroes")

        Then assert body_is(ordered = true, expected = """
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
          },
          {
            "name": "Scalaman",
            "realName": "Oleg Ilyenko",
            "hasSuperpowers": true,
            "city": "Pankow"
          }]""", ignoring = "publisher")

        Then assert body_is(ordered = true, expected = """
          |    name     |    realName    |     city      |  hasSuperpowers |
          | "Batman"    | "Bruce Wayne"  | "Gotham city" |      false      |
          | "Superman"  | "Clark Kent"   | "Metropolis"  |      true       |
          | "Spiderman" | "Peter Parker" | "New York"    |      true       |
          | "IronMan"   | "Tony Stark"   | "New York"    |      false      |
          | "Scalaman"  | "Oleg Ilyenko" | "Pankow"      |      true       |
        """, ignoring = "publisher")

        Then assert body_is(ordered = false, expected = """
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
            "name": "Scalaman",
            "realName": "Oleg Ilyenko",
            "city": "Pankow"
          }]""", ignoring = "hasSuperpowers", "publisher")

        Then assert response_array_size_is(5)

        And assert response_array_contains(
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

        When I DELETE(s"$baseUrl/superheroes/IronMan")

        Then assert status_is(200)

        And I GET(s"$baseUrl/superheroes")

        Then assert response_array_size_is(4)

        And assert_not response_array_contains(
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
      },

      Scenario("Session feature demo") { implicit b ⇒

        When I GET(s"$baseUrl/superheroes/Batman")

        Then assert body_is(
          """
          {
            "name": "Batman",
            "realName": "Bruce Wayne",
            "city": "Gotham city",
          }
          """, ignoring = "hasSuperpowers", "publisher"
        )

        // Set a key/value in the Scenario's session
        And I save("favorite-superhero" → "Batman")

        Then assert session_contains("favorite-superhero" → "Batman")

        // Retrieve dynamically from session with <key> for URL construction
        When I GET(s"$baseUrl/superheroes/<favorite-superhero>")

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
        And I extract_from_response("city", "batman-city")

        // Or with extractor
        And I extract_from_response(_ \ "city", "batman-city")

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
      },

      Scenario("Advanced feature demo") { implicit b ⇒

        When I GET(s"$baseUrl/superheroes/Batman")

        And assert body_against_schema(s"$baseUrl/superhero.schema.json")

        // Repeat series of Steps
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

        Then assert body_is("""
          |   eventType      |    data     |
          | "superhero name" |  "Batman"   |
          | "superhero name" | "Superman"  |
          | "superhero name" | "Spiderman" |
          | "superhero name" | "Scalaman"  |
        """)

        // Repeat series of Steps until it succeed
        Eventually(maxDuration = 15.seconds, interval = 200.milliseconds) {

          When I GET(s"$baseUrl/superheroes/random")

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
    )
}