package com.github.agourlay.cornichon.framework.examples.superHeroes

import java.nio.charset.StandardCharsets
import java.util.Base64
import com.github.agourlay.cornichon.CornichonFeature
import com.github.agourlay.cornichon.core.Step
import com.github.agourlay.cornichon.core.Resource
import com.github.agourlay.cornichon.framework.examples.HttpServer
import com.github.agourlay.cornichon.framework.examples.superHeroes.server.SuperHeroesHttpAPI
import com.github.agourlay.cornichon.http.HttpService
import com.github.agourlay.cornichon.json.CornichonJson._
import com.github.agourlay.cornichon.resolver.JsonMapper
import com.github.agourlay.cornichon.steps.wrapped.ScenarioResourceStep
import sangria.macros._
import scala.concurrent.Await
import scala.concurrent.duration._

class SuperHeroesScenario extends CornichonFeature {

  def feature =
    Feature("Cornichon feature example") {

      Scenario("demonstrate CRUD features") {

        When I get("/superheroes/Batman").withParams("sessionId" -> "<session-id>")

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

        And assert body.ignoring("city", "publisher").is(
          """
          {
            "name": "Batman",
            "realName": "Bruce Wayne",
            "hasSuperpowers": false
          }
          """
        )

        And assert body.ignoring("publisher.name", "publisher.location").is(
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
        And assert body.whitelisting.is(
          """
          {
            "name": "Batman",
            "realName": "Bruce Wayne"
          }
          """
        )

        // Test part of response body by providing a JsonPath
        Then assert body.path("city").isPresent

        Then assert body.path("city").is("Gotham city")

        Then assert body.path("city").containsString("Gotham")

        Then assert body.path("city").matchesRegex(".*ham.*ty".r)

        Then assert body.path("name").matchesRegex(".*man".r)

        Then assert body.path("name").isNot("Superman")

        Then assert body.path("name").isNot("*any-integer*")

        Then assert body.path("publisher").isNot(
          """
          {
            "name":"Marvel",
            "foundationYear":*any-string*,
            "location":"Burbank, California"
          }
          """
        )

        Then assert body.path("country").isAbsent

        Then assert body.path("hasSuperpowers").is(false)

        Then assert body.path("publisher").is(
          """
          {
            "name":"DC",
            "foundationYear":1934,
            "location":"Burbank, California"
          }
          """
        )

        Then assert body.path("publisher").ignoring("location").is(
          """
          {
            "name":"DC",
            "foundationYear":1934
          }
          """
        )

        Then assert body.path("publisher.name").is("DC")

        Then assert body.path("publisher.foundationYear").is(1934)

        Then assert body.path("publisher.foundationYear").isLessThan(1935)

        Then assert body.path("publisher.foundationYear").isGreaterThan(1933)

        Then assert body.path("publisher.foundationYear").isBetween(1933, 1935)

        When I get("/superheroes/Scalaman").withParams("sessionId" -> "<session-id>")

        Then assert status.is(404)

        And assert body.is(
          """
          {
            "error": "Superhero Scalaman not found"
          }
          """
        )

        When I post("/superheroes").withParams(
          "sessionId" -> "<session-id>"
        ).withBody(gqljson"""
          {
            name: "Scalaman"
            realName: "Oleg Ilyenko"
            city: "Berlin"
            hasSuperpowers: false
            publisher:{
              name:"DC"
              foundationYear:1934
              location:"Burbank, California"
            }
          }
          """)

        Then assert status.is(401)
        //The resource requires authentication, which was not supplied with the request

        // Try again with authentication
        When I post("/superheroes").withBody(
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
          """
        ).withParams("sessionId" -> "<session-id>")
          .withHeaders(("Authorization", "Basic " + Base64.getEncoder.encodeToString("admin:cornichon".getBytes(StandardCharsets.UTF_8))))

        Then assert status.is(201)

        Then assert status.isSuccess

        When I get("/superheroes/Scalaman").withParams("sessionId" -> "<session-id>")

        Then assert body.ignoring("publisher", "hasSuperpowers", "city").is(
          """
          {
            "name": "Scalaman",
            "realName": "Oleg Ilyenko"
          }
          """
        )

        // Params can also be passed in the URL
        When I get("/superheroes/Scalaman?sessionId=<session-id>").withParams(
          "protectIdentity" -> "true"
        )

        Then assert body.ignoring("publisher").is(
          """
          {
            "name": "Scalaman",
            "realName": "XXXXX",
            "hasSuperpowers": false,
            "city": "Berlin"
          }
          """
        )

        WithBasicAuth("admin", "cornichon") {
          When I put("/superheroes").withParams("sessionId" -> "<session-id>").withBody(
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
            """
          )

          Then assert body.path("city").is("Pankow")
        }

        Then assert status.is(200)

        Then assert body.ignoring("publisher").is(
          """
          {
            "name": "Scalaman",
            "realName": "Oleg Ilyenko",
            "hasSuperpowers": true,
            "city": "Pankow"
          }
          """
        )

        When I get("/superheroes/GreenLantern").withParams("sessionId" -> "<session-id>")

        Then assert status.is(200)

        Then assert status.isSuccess

        When I delete("/superheroes/GreenLantern").withParams("sessionId" -> "<session-id>")

        When I get("/superheroes/GreenLantern").withParams("sessionId" -> "<session-id>")

        Then assert status.is(404)

        Then assert status.isClientError
      }

      Scenario("demonstrate collection features") {

        When I get("/superheroes").withParams("sessionId" -> "<session-id>")

        Then assert body.asArray.isNotEmpty

        Then assert body.asArray.ignoringEach("publisher").is(
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
          }]
          """
        )

        Then assert body.asArray.ignoringEach("publisher").is(
          """
          |      name      |    realName    |     city      |  hasSuperpowers |
          |    "Batman"    | "Bruce Wayne"  | "Gotham city" |      false      |
          |   "Superman"   | "Clark Kent"   | "Metropolis"  |      true       |
          | "GreenLantern" | "Hal Jordan"   | "Coast City"  |      true       |
          |   "Spiderman"  | "Peter Parker" | "New York"    |      true       |
          |    "IronMan"   | "Tony Stark"   | "New York"    |      false      |
        """
        )

        Then assert body.asArray.ignoringEach("hasSuperpowers", "publisher").is(
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
          }]
          """
        )

        Then assert body.asArray.hasSize(5)

        Then assert body.asArray.size.isLessThan(6)

        Then I save("5th-name" -> "IronMan")

        And assert body.asArray.ignoringEach("city", "hasSuperpowers").contains(
          """
          {
            "name": "<5th-name>",
            "realName": "Tony Stark",
            "publisher":{
              "name":"Marvel",
              "foundationYear":1939,
              "location":"135 W. 50th Street, New York City"
            }
          }
          """
        )

        And assert body.path("$[*].name").asArray.is(
          """
            [ "Spiderman", "IronMan", "Superman", "GreenLantern", "Batman"]
          """
        )

        When I delete("/superheroes/IronMan").withParams("sessionId" -> "<session-id>")

        Then assert status.is(200)

        And I get("/superheroes").withParams("sessionId" -> "<session-id>")

        Then assert body.asArray.hasSize(4)

        And assert body.asArray.not_contains(
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

        When I get("/superheroes/Batman").withParams("sessionId" -> "<session-id>")

        Then assert body.ignoring("hasSuperpowers", "publisher").is(
          """
          {
            "name": "Batman",
            "realName": "Bruce Wayne",
            "city": "Gotham city"
          }
          """
        )

        // Set a key/value in the Scenario's session
        And I save("favorite-superhero" -> "Batman")

        Then assert session_value("favorite-superhero").is("Batman")

        // Retrieve dynamically from session with <key> for URL construction
        When I get("/superheroes/<favorite-superhero>").withParams("sessionId" -> "<session-id>")

        Then assert body.ignoring("hasSuperpowers", "publisher").is(
          """
          {
            "name": "<favorite-superhero>",
            "realName": "Bruce Wayne",
            "city": "Gotham city"
          }
          """
        )

        And assert session_value("batman-city").isAbsent

        // Extract value from response into session for reuse
        And I save_body_path("city" -> "batman-city")

        And assert session_value("batman-city").isPresent

        Then assert session_value("batman-city").isNot("Metropolis")

        Then assert session_value("batman-city").is("Gotham city")

        Then assert body.ignoring("hasSuperpowers", "publisher").is(
          """
          {
            "name": "<favorite-superhero>",
            "realName": "Bruce Wayne",
            "city": "<batman-city>"
          }
          """
        )

        // To make debugging easier, here are some debug steps printing into console
        //And I show_session
        //And I show_last_status
        //And I show_last_body
        //And I show_last_headers
      }

      Scenario("demonstrate GraphQL support") {

        When I query_gql("/graphql").withVariables("sessionId" -> "<session-id>").withQuery {
          graphql"""
            query($$sessionId: String!) {
             superheroByName(sessionId: $$sessionId, name: "Batman", protectIdentity: false) {
               name
               city
             }
            }
          """
        }

        Then assert status.is(200)

        Then assert body.path("data.superheroByName").is(
          """
          {
            "name": "Batman",
            "city": "Gotham city"
          }
          """
        )

        // Support for GraphQL JSON input for lightweight definition
        // Requires the import of com.github.agourlay.cornichon.json.CornichonJson._
        Then assert body.path("data.superheroByName").is(
          gqljson"""
          {
            name : "Batman"
            city : "Gotham city"
          }
          """
        )

        When I query_gql("/graphql").withVariables("sessionId" -> "<session-id>").withQuery {
          graphql"""
            mutation ($$sessionId: String!) {
             updateSuperhero(
               sessionId: $$sessionId,
               s: {
                 name: "Batman",
                 realName: "Bruce Wayne",
                 city: "Berlin",
                 hasSuperpowers: false,
                 publisher: {
                   name: "DC",
                   foundationYear: 2016,
                   location: "Pankow"
                 }
               }
             )
             {
               name
               city
             }
            }
          """
        }

        Then assert status.is(200)

        Then assert body.path("data.updateSuperhero").is(
          """
          {
            "name": "Batman",
            "city": "Berlin"
          }
          """
        )
      }

      Scenario("demonstrate wrapping DSL blocks") {

        When I get("/superheroes/Batman").withParams("sessionId" -> "<session-id>")

        // Using registered extractor at the bottom of this file
        Then assert body.path("name").is("<name>")

        // Repeat series of Steps
        Repeat(2) {

          When I get("/superheroes/Batman").withParams("sessionId" -> "<session-id>")

          Then assert status.is(200)
        }

        // Repeat series of Steps during a period of time
        RepeatDuring(10.millis) {

          When I get("/superheroes/Batman").withParams("sessionId" -> "<session-id>")

          Then assert status.is(200)
        }

        // Nested Repeats
        Repeat(2) {

          When I get("/superheroes/Superman").withParams("sessionId" -> "<session-id>")

          Then assert status.is(200)

          Repeat(2) {

            When I get("/superheroes/Batman").withParams("sessionId" -> "<session-id>")

            Then assert status.is(200)
          }
        }

        // Execute steps 10 times with parallelism factor of 3
        RepeatConcurrently(times = 10, parallelism = 3, maxTime = 20.seconds) {

          When I get("/superheroes/Batman").withParams("sessionId" -> "<session-id>")

          Then assert status.is(200)
        }

        // Repeat series of Steps until it succeed
        Eventually(maxDuration = 3.seconds, interval = 10.milliseconds) {

          When I get("/superheroes/random").withParams("sessionId" -> "<session-id>")

          Then assert body.path("name").matchesRegex(".*man".r)

          Then assert body.ignoring("hasSuperpowers", "publisher").is(
            """
            {
              "name": "Batman",
              "realName": "Bruce Wayne",
              "city": "Gotham city"
            }
            """
          )
        }

        // Nesting different kind of blocs
        Repeat(1) {

          When I get("/superheroes/Superman").withParams("sessionId" -> "<session-id>")

          Then assert status.is(200)

          Eventually(maxDuration = 3.seconds, interval = 10.milliseconds) {

            When I get("/superheroes/random").withParams("sessionId" -> "<session-id>")

            Then assert body.ignoring("hasSuperpowers", "publisher").is(
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

        // Repeat for each element
        RepeatWith("Superman", "GreenLantern", "Spiderman")("superhero-name") {

          When I get("/superheroes/<superhero-name>").withParams("sessionId" -> "<session-id>")

          Then assert status.is(200)

          Then assert body.path("hasSuperpowers").is(true)
        }

        // Repeat for each element
        RepeatFrom("Superman" :: "GreenLantern" :: "Spiderman" :: Nil)("superhero-name") {

          When I get("/superheroes/<superhero-name>").withParams("sessionId" -> "<session-id>")

          Then assert status.is(200)

          Then assert body.path("hasSuperpowers").is(true)
        }

        // Retry series of Steps with a limit
        RetryMax(300) {

          When I get("/superheroes/random").withParams("sessionId" -> "<session-id>")

          Then assert body.ignoring("hasSuperpowers", "publisher").is(
            """
            {
              "name": "Batman",
              "realName": "Bruce Wayne",
              "city": "Gotham city"
            }
            """
          )
        }

        // Assert that a series of Steps succeeds within a given duration
        Within(maxDuration = 50.millis) {

          When I wait(10.millis)

        }

        // Blocks can be nested
        RepeatConcurrently(times = 10, parallelism = 2, maxTime = 20.seconds) {

          Eventually(maxDuration = 10.seconds, interval = 10.milliseconds) {

            When I get("/superheroes/random").withParams("sessionId" -> "<session-id>")

            Then assert body.ignoring("hasSuperpowers", "publisher").is(
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

        // SSE streams are aggregated over a period of time into a JSON Array
        When I open_sse("/sseStream/superheroes", takeWithin = 1.seconds).withParams(
          "sessionId" -> "<session-id>",
          "justName" -> "true"
        )

        Then assert body.asArray.hasSize(5)

        Then assert body.asArray.is(
          """
            |    eventType     |      data      |  id  | retry | comment |
            | "superhero name" |    "Batman"    | null | null  |   null  |
            | "superhero name" |   "Superman"   | null | null  |   null  |
            | "superhero name" | "GreenLantern" | null | null  |   null  |
            | "superhero name" |   "Spiderman"  | null | null  |   null  |
            | "superhero name" |    "IronMan"   | null | null  |   null  |
           """
        )
      }

      Scenario("demonstrate DSL composition") {

        Then assert superhero_exists("Batman")

        Then assert random_superheroes_until("Batman")

      }

      Scenario("demonstrate matchers features") {

        And I save("favorite-superhero" -> "Batman")

        When I get("/superheroes/<favorite-superhero>").withParams("sessionId" -> "<session-id>")

        Then assert status.is(200)

        And assert body.path("city").is("*any-string*")

        And assert body.ignoring("city", "realName", "publisher.location").is(
          """
          {
            "name": "<favorite-superhero>",
            "hasSuperpowers": *any-boolean*,
            "publisher": {
              "name": *any-string*,
              "foundationYear": *any-positive-integer*
            }
          }
          """
        )
      }

      Scenario("demonstrate resource steps") {
        When I get("/superheroes/Flash").withParams("sessionId" -> "<session-id>")
        Then assert status.is(404)

        WithSuperhero("Flash") {

          When I get("/superheroes/Flash").withParams("sessionId" -> "<session-id>")

          Then assert status.is(200)
        }

        When I get("/superheroes/Flash").withParams("sessionId" -> "<session-id>")
        Then assert status.is(404)
      }
    }

  def superhero_exists(name: String): Step =
    AttachAs("superhero exists") {
      When I get(s"/superheroes/$name").withParams("sessionId" -> "<session-id>")
      Then assert status.is(200)
    }

  def random_superheroes_until(name: String): Step =
    Eventually(maxDuration = 3.seconds, interval = 10.milliseconds) {
      When I get("/superheroes/random").withParams("sessionId" -> "<session-id>")
      Then assert body.path("name").is(name)
    }

  // Create a superhero resource that will be cleaned up at the end of the scope
  def WithSuperhero(name: String) = WithResource(superhero_resource(name))

  def superhero_resource(name: String) = Resource(
    title = s"superhero $name resource",
    acquire = post("/superheroes")
      .withBody(
        s"""
        {
          "name": "$name",
          "realName": "unknown",
          "city": "Berlin",
          "hasSuperpowers": true,
          "publisher": {
            "name":"DC",
            "foundationYear":1934,
            "location":"Burbank, California"
          }
        }
        """)
      .withParams("sessionId" -> "<session-id>")
      .withHeaders(("Authorization", "Basic " + Base64.getEncoder.encodeToString("admin:cornichon".getBytes(StandardCharsets.UTF_8)))),
    release = delete(s"/superheroes/<name>").withParams("sessionId" -> "<session-id>")
  )

  lazy val port = 8080

  // Base url used for all HTTP steps
  override lazy val baseUrl = s"http://localhost:$port"

  //Travis CI struggles with default value `2.seconds`
  override lazy val requestTimeout = 5.second

  var server: HttpServer = _

  // Starts up test server
  beforeFeature {
    server = Await.result(new SuperHeroesHttpAPI().start(port), 5.second)
  }

  // Stops test server
  afterFeature {
    Await.result(server.shutdown(), 5.second)
  }

  def session_resource = ScenarioResourceStep(
    title = "session resource",
    acquire = post("/session"),
    release = delete("/session").withParams("sessionId" -> "<session-id>")
  )

  // Step to be executed before each scenario
  beforeEachScenario {

    AttachAs("Setup session") {

      Given a session_resource

      Then assert status.is(201)

      And I save_body("session-id")

    }

  }

  override def registerExtractors: Map[String, JsonMapper] = Map(
    "name" -> JsonMapper(HttpService.SessionKeys.lastResponseBodyKey, "name")
  )
}
