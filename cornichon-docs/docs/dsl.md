---
layout: docs
title:  "DSL"
position: 3
---

# DSL

The content of a `feature` is described using a domain-specific language (DSL) providing a clear structure for statement definitions.

The structure of a step statement is the following:

1 - starts with either `Given` - `When` - `And` - `Then`

The prefixes do not change the behavior of the steps but are present to improve the readability.


2 - followed by any single word (could be several words wrapped in back-ticks)

This structure was chosen to increase the freedom of customization while still benefiting from Scala's infix notation.


3 - ending with a `step` definition

The usage pattern is often to first run a `step` with a side effect then assert an expected state in a second `step`.

For example :

```scala
Given I step_definition

When a step_definition

And \`another really important\` step_definition

Then assert step_definition
```

`step_definition` stands here for any object of type `Step`, those can be manually defined or simply built-in in Cornichon.


# Built-in steps

Cornichon has a set of built-in steps for various HTTP calls and assertions on the response.


## HTTP effects

- GET, DELETE, HEAD, OPTIONS, POST, PUT and PATCH use the same request builder for request's body, URL parameters and headers.

```scala
head("http://superhero.io/daredevil")

get("http://superhero.io/daredevil").withParams(
  "firstParam" -> "value1",
  "secondParam" -> "value2")

delete("http://superhero.io/daredevil").withHeaders(("Authorization", "Basic QWxhZGRpbjpvcGVuIHNlc2FtZQ=="))

post("http://superhero.io/batman").withBody("JSON description of Batman goes here")

put("http://superhero.io/batman").withBody("JSON description of Batman goes here").withParams(
  "firstParam" -> "value1",
  "secondParam" -> "value2")

patch("http://superhero.io/batman").withBody("JSON description of Batman goes here")
```


There is a built-in support for HTTP body defined as String, if you wish to use other types please check out the section [Custom HTTP body type](misc.md#custom-http-body-type).

## HTTP assertions

- assert response status

```scala
status.is(200)
```

- assert response headers

```scala
headers.name("cache-control").isPresent

headers.contain("cache-control" -> "no-cache")

headers.name("cache_control").isAbsent

save_header_value("cache_control" -> "my-cache-control-value")
```

- assert response body comes with different flavors (ignoring, whitelisting)

```scala
body.is(
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
  """)

body.ignoring("city", "hasSuperpowers", "publisher.foundationYear", "publisher.location").is(
  """
  {
    "name": "Batman",
    "realName": "Bruce Wayne",
    "publisher":{
      "name":"DC"
    }
  }
  """)

body.whitelisting.is(
  """
  {
    "name": "Batman",
    "realName": "Bruce Wayne",
    "publisher":{
      "name":"DC"
    }
  }
  """)
```

Ignored keys and extractors are JsonPaths following the format "a.b.c[index].d".

The `index` value is either:

- an Integer addressing the array position.
- a `*` to target all values, the result will be an array of the projected values.

JsonPath can also be used to only assert part of the response

```scala
body.path("city").is("Gotham city")

body.path("hasSuperpowers").is(false)

body.path("publisher.name").is("DC")

body.path("city").containsString("Gotham")

body.path("superheroes[*].name").is("""[ "Spiderman", "IronMan", "Superman", "GreenLantern", "Batman" ]""")

body.path("publisher.foundationYear").is(1934)

body.path("publisher.foundationYear").isPresent

body.path("publisher.foundationMonth").isAbsent
```

It is possible to handle null values, given the following response body `{ “data” : null }`

```scala
body.path("data").isAbsent  //incorrect
body.path("data").isPresent //correct
body.path("data").isNull    //correct
body.path("data").isNotNull //incorrect
```

If one key of the path contains a "." it has to be wrapped with "`" to notify the parser.

```scala
body.path("`message.en`").isPresent

body.path("`message.fr`").isAbsent
```

To address a root array use `$` followed by the index the access.

```scala

body.path("$[2].name")
```

If the endpoint returns a collection assert response body has several options (ordered, ignoring and using data table)

```scala
body.asArray.inOrder.ignoringEach("city", "hasSuperpowers", "publisher").is(
  """
  [{
    "name": "Batman",
    "realName": "Bruce Wayne"
  },
  {
    "name": "Superman",
    "realName": "Clark Kent"
  }]
  """)

body.asArray.inOrder.ignoringEach("publisher").is(
 """
  |    name     |    realName    |     city      |  hasSuperpowers |
  | "Batman"    | "Bruce Wayne"  | "Gotham city" |      false      |
  | "Superman"  | "Clark Kent"   | "Metropolis"  |      true       |
 """)

body.asArray.hasSize(2)
body.asArray.size.is(2) //equivalent to above
body.asArray.size.isLesserThan(3)
body.asArray.size.isGreaterThan(1)
body.asArray.size.isBetween(1, 3)

body.asArray.isNotEmpty

body.asArray.contains(
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
  """)
```

It is important to mention that `body` expects a JSON content!
When receiving non JSON payloads, use `body_raw` which offers `String` like assertions.

```scala
body_raw.containsString("xml")
```

## HTTP streams

- Server-Sent-Event.

```scala
When I open_sse(s"http://superhero.io/stream", takeWithin = 1.seconds).withParams("justName" -> "true")

Then assert body.asArray.hasSize(2)

Then assert body.is("""
  |   eventType      |    data     |  id  | retry | comment |
  | "superhero name" |  "Batman"   | null | null  |   null  |
  | "superhero name" | "Superman"  | null | null  |   null  |
""")
```

SSE streams are aggregated over a period of time in an array, therefore the previous array predicates can be re-used.


## GraphQL support

Cornichon offers an integration with the library [Sangria](https://github.com/sangria-graphql/sangria) to propose convenient features to test GraphQL API.


- GraphQL query

```scala
import sangria.macros._

 When I query_gql("/<project-key>/graphql").withQuery(
    graphql"""
      query MyQuery {
        superheroes {
          results {
            name
            realName
            publisher {
              name
            }
          }
        }
      }
    """
    )
```

`query_gql` can also be used for mutation query.


- GraphQL JSON

all built-in steps accepting String input/output can also accept an alternative lightweight JSON format using the `gqljson` StringContext.

```scala
import com.github.agourlay.cornichon.json.CornichonJson._

And assert body.ignoring("city", "publisher").is(
  gqljson"""
  {
    name: "Batman",
    realName: "Bruce Wayne",
    hasSuperpowers: false
  }
  """)
```


## Session steps

- setting a value in `session`

```scala
save("favorite-superhero" -> "Batman")
```

- saving value to `session`

```scala
save_body_path("city" -> "batman-city")
```

- asserting value in `session`

```scala
session_value("favorite-superhero").is("Batman")
```

- asserting JSON value in `session`

```scala
session_value("my-json-response").asJson.path("a.b.c").ignoring("d").is("...")
```


- asserting existence of value in `session`

```scala
session_value("favorite-superhero").isPresent
session_value("favorite-superhero").isAbsent
````

- transforming a value in `session`

```scala
transform_session("my-key")(_.toUpperCase)
```

### Wrapper steps

Wrapper steps allow to control the execution of a series of steps to build more powerful tests.

- repeating a series of `steps`

```scala
Repeat(3) {
  When I get("http://superhero.io/batman")

  Then assert status.is(200)
}
```

- repeating a series of `steps` during a period of time

```scala
RepeatDuring(300.millis) {
  When I get("http://superhero.io/batman")

  Then assert status.is(200)
}
```

- repeat a series of `steps` for each input element

```scala
RepeatWith("Superman", "GreenLantern", "Spiderman")("superhero-name") {

  When I get("/superheroes/<superhero-name>").withParams("sessionId" -> "<session-id>")

  Then assert status.is(200)

  Then assert body.path("hasSuperpowers").is(true)
}
```

- retry a series of `steps` until it succeeds or reaches the limit

```scala
RetryMax(3) {
  When I get("http://superhero.io/batman")

  Then assert status.is(200)
}
```


- repeating a series of `steps` until it succeeds over a period of time at a specified interval (handy for eventually consistent endpoints)

```scala
Eventually(maxDuration = 15.seconds, interval = 200.milliseconds) {

    When I get("http://superhero.io/random")

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
```

- execute a series of steps 'n' times by batch of `p` in parallel and wait 'maxTime' for completion.

```scala
RepeatConcurrently(times = 10, parallel = 3, maxTime = 10 seconds) {

  When I get("http://superhero.io/batman")

  Then assert status.is(200)
}
```

- execute each step in parallel and wait 'maxTime' for completion.

```scala
Concurrently(maxTime = 10 seconds) {

  When I get("http://superhero.io/batman")

  When I get("http://superhero.io/superman")
}
```


- execute a series of steps and fails if the execution does not complete within 'maxDuration'.

```scala
Within(maxDuration = 10 seconds) {

  When I get("http://superhero.io/batman")

  Then assert status.is(200)
}
```

- repeat a series of steps with different inputs specified via a data-table

```scala
WithDataInputs(
  """
    | a | b  | c  |
    | 1 | 3  | 4  |
    | 7 | 4  | 11 |
    | 1 | -1 | 0  |
  """
) {
  Then assert a_plus_b_equals_c
}

def a_plus_b_equals_c =
  AssertStep("sum of 'a' + 'b' = 'c'", s ⇒ GenericEqualityAssertion(s.getUnsafe("a").toInt + s.getUnsafe("b").toInt, s.getUnsafe("c").toInt))
```

- WithHeaders automatically sets headers for several steps useful for an authenticated scenario.

```scala
WithHeaders(("Authorization", "Basic QWxhZGRpbjpvcGVuIHNlc2FtZQ==")){
  When I get("http://superhero.io/secured")
  Then assert status.is(200)
}
```

- WithBasicAuth automatically sets basic auth headers for several steps.

```scala
WithBasicAuth("admin", "root"){
  When I get("http://superhero.io/secured")
  Then assert status.is(200)
}
```

- HttpListenTo creates an HTTP server that will be running during the length of the enclosed steps.

This feature is defined the module `cornichon-http-mock` and requires to extend the trait `HttpMockDsl`.

By default, this server responds with 201 to any POST request and 200 for all the rest.

Additionally, it provides three administrations features:
- fetching recorded received requests
- resetting recorded received requests
- toggling on/off the error mode to return HTTP 500 to incoming requests

The server records all requests received as a JSON array of HTTP request for later assertions.

There are two ways to perform assertions on the server statistics, either by querying the session at the end of the block or by contacting directly the server while it runs.

Refer to those [examples](https://github.com/agourlay/cornichon-http-mock/blob/master/src/test/scala/com/github/agourlay/cornichon/examples/MockServerExample.scala) for more information.

This feature is experimental and may change in the future.

- Log duration

By default, all `Step` execution time can be found in the logs, but sometimes one needs to time a series of steps.

This is where `LogDuration` comes in handy, it requires a label that will be printed as well to identify results.

```scala
LogDuration(label = "my experiment") {

  When I get("http://superhero.io/batman")

  Then assert status.is(200)
}
```

## Debug steps

- showing session content for debugging purpose

```scala
 And I show_session

 And I show_last_response

 And I show_last_response_json (pretty print the json body)

 And I show_last_status

 And I show_last_body

 And I show_last_body_json (pretty print the json body)

 And I show_last_headers
```


Those descriptions might be already outdated, in case of doubt always refer to those [examples](https://github.com/agourlay/cornichon/blob/master/src/test/scala/com/github/agourlay/cornichon/examples/superHeroes/SuperHeroesScenario.scala) as they are executed as part of Cornichon's test suite.

## DSL composition

Series of steps defined with Cornichon's DSL can be reused within different `Scenarios`.

Using the keyword `Attach` if the series starts with a `Step` and without if it starts with a wrapping bloc.

```scala mdoc:silent
import com.github.agourlay.cornichon.CornichonFeature
import scala.concurrent.duration._

class CompositionFeature extends CornichonFeature {

  def feature =
    Feature("Cornichon feature example") {

      Scenario("demonstrate DSL composition") {

        Then assert superhero_exists("batman")

        Then assert random_superheroes_until("Batman")

      }
    }

  def superhero_exists(name: String) =
    Attach {
      When I get(s"/superheroes/$name").withParams("sessionId" -> "<session-id>")
      Then assert status.is(200)
    }

  def random_superheroes_until(name: String) =
    Eventually(maxDuration = 3.seconds, interval = 10.milliseconds) {
      When I get("/superheroes/random").withParams("sessionId" -> "<session-id>")
      Then assert body.path("name").is(name)
      Then I print_step("bingo!")
    }
}
```

It is possible to give a title to an attached bloc using `AttachAs(title)`.
