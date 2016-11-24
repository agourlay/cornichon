cornichon [![Build Status](https://travis-ci.org/agourlay/cornichon.svg?branch=master)](https://travis-ci.org/agourlay/cornichon) [![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.github.agourlay/cornichon_2.11/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.github.agourlay/cornichon_2.11) [![License](http://img.shields.io/:license-Apache%202-brightgreen.svg)](http://www.apache.org/licenses/LICENSE-2.0.txt) [![Join the chat at https://gitter.im/agourlay/cornichon](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/agourlay/cornichon?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)
=========

An extensible Scala DSL for testing JSON HTTP APIs.

## Table of contents

1. [Quick start](#quick-start)
2. [Structure](#structure)
3. [DSL](#dsl)
4. [Built-in steps](#built-in-steps)
  1. [HTTP effects](#http-effects)
  2. [HTTP assertions](#http-assertions)
  3. [HTTP streams](#http-streams)
  4. [GraphQL support](#graphql-support)
  5. [Session steps](#session-steps)
  6. [Wrapper steps](#wrapper-steps)
  7. [Debug steps](#debug-steps)
5. [DSL composition](#dsl-composition)
6. [Placeholders](#placeholders)
7. [Custom steps](#custom-steps)
  1. [Effects and Assertions](#effects-and-assertions)
  2. [HTTP service](#http-service)
8. [Feature options](#feature-options)
  1. [Before and after hooks](#before-and-after-hooks)
  2. [Base URL](#base-url)
  3. [Request timeout](#request-timeout)
  4. [Register custom extractors](#register-custom-extractors)
9. [Execution model](#execution-model)
10. [Ignoring features or scenarios](#ignoring-features-or-scenarios)
11. [Custom HTTP body type](#custom-http-body-type)
12. [ScalaTest integration](#scalatest-integration)
13. [SSL configuration](#ssl-configuration)
14. [License](#license)

## Quick start

Add the library dependency

``` scala
libraryDependencies += "com.github.agourlay" %% "cornichon" % "0.10.2" % "test"
```

Cornichon is currently integrated with [ScalaTest](http://www.scalatest.org/), place your ```Feature``` files inside ```src/test/scala``` and run them using ```sbt test```.

A ```Feature``` is a class extending ```CornichonFeature``` and implementing the required ```feature``` function.
 
How does it look like? 

Find below an example of testing the Open Movie Database API.

```scala

import com.github.agourlay.cornichon.CornichonFeature

class ReadmeExample extends CornichonFeature {

  def feature = Feature("OpenMovieDatabase API"){

    Scenario("list GOT season 1 episodes"){

      When I get("http://www.omdbapi.com").withParams(
        "t" -> "Game of Thrones",
        "Season" -> "1"
      )

      Then assert status.is(200)

      And assert body.ignoring("Episodes", "Response").is(
        """
        {
          "Title": "Game of Thrones",
          "Season": "1"
        }
        """)

      And assert body.path("Episodes").is(
        """
        |                Title                    |   Released   | Episode | imdbRating |   imdbID    |
        | "Winter Is Coming"                      | "2011-04-17" |   "1"   |    "8.1"   | "tt1480055" |
        | "The Kingsroad"                         | "2011-04-24" |   "2"   |    "7.8"   | "tt1668746" |
        | "Lord Snow"                             | "2011-05-01" |   "3"   |    "7.6"   | "tt1829962" |
        | "Cripples, Bastards, and Broken Things" | "2011-05-08" |   "4"   |    "7.7"   | "tt1829963" |
        | "The Wolf and the Lion"                 | "2011-05-15" |   "5"   |    "8.0"   | "tt1829964" |
        | "A Golden Crown"                        | "2011-05-22" |   "6"   |    "8.1"   | "tt1837862" |
        | "You Win or You Die"                    | "2011-05-29" |   "7"   |    "8.1"   | "tt1837863" |
        | "The Pointy End"                        | "2011-06-05" |   "8"   |    "7.9"   | "tt1837864" |
        | "Baelor"                                | "2011-06-12" |   "9"   |    "8.6"   | "tt1851398" |
        | "Fire and Blood"                        | "2011-06-19" |  "10"   |    "8.4"   | "tt1851397" |
        """)

      And assert body.path("Episodes").asArray.hasSize(10)

      And assert body.path("Episodes[0]").is(
        """
        {
          "Title": "Winter Is Coming",
          "Released": "2011-04-17",
          "Episode": "1",
          "imdbRating": "8.1",
          "imdbID": "tt1480055"
        }
        """)

      And assert body.path("Episodes[0].Released").is("2011-04-17")

      And assert body.path("Episodes").asArray.contains(
        """
        {
          "Title": "Winter Is Coming",
          "Released": "2011-04-17",
          "Episode": "1",
          "imdbRating": "8.1",
          "imdbID": "tt1480055"
        }
        """)
    }
  }
}
```

For more examples see the following files which are part of the test pipeline:

- [Embedded Superheroes API](https://github.com/agourlay/cornichon/blob/master/src/test/scala/com/github/agourlay/cornichon/examples/superHeroes/SuperHeroesScenario.scala).

- [OpenMovieDatabase API](https://github.com/agourlay/cornichon/blob/master/src/it/scala/com.github.agourlay.cornichon.examples/OpenMovieDatabase.scala).

- [DeckOfCard API](https://github.com/agourlay/cornichon/blob/master/src/it/scala/com.github.agourlay.cornichon.examples/DeckOfCard.scala).

- [Star Wars API](https://github.com/agourlay/cornichon/blob/master/src/it/scala/com.github.agourlay.cornichon.examples/StarWars.scala).

- [Math Operations](https://github.com/agourlay/cornichon/blob/master/src/test/scala/com/github/agourlay/cornichon/examples/math/MathScenario.scala).

## Structure

A Cornichon test is the definition of a so-called ```feature```. 

A ```feature``` can have several ```scenarios``` which in turn can have several ```steps```.

The example below contains one ```feature``` with one ```scenario``` with two ```steps```.

```scala
class CornichonExamplesSpec extends CornichonFeature {

  def feature = Feature("Checking google"){
  
    Scenario("Google is up and running"){
  
      When I get("http://google.com")

      Then assert status.is(302)
    }
  }
}
```

The failure modes are the following:


- A ```feature``` fails if one or more ```scenarios``` fail.

- A ```scenario``` fails if at least one ```step``` fails.

- A ```scenario``` will stop at the first failed step encountered and ignore the remaining ```steps```.


## DSL

The content of a ```feature``` is described using a domain-specific language (DSL) providing a clear structure for statement definitions.

The structure of a step statement is the following:

1 - starts with either ```Given``` - ```When``` - ```And``` - ```Then```

The prefixes do not change the behavior of the steps but are present to improve the readability.


2 - followed by any single word (could be several words wrapped in back-ticks)

This structure was chosen to increase the freedom of customization while still benefiting from Scala's infix notation.


3 - ending with a ```step``` definition

The usage pattern is often to first run a ```step``` with a side effect then assert an expected state in a second ```step```.

For example :

```scala
Given I step_definition

When a step_definition

And \`another really important\` step_definition

Then assert step_definition

```

```step_definition``` stands here for any object of type ```Step```, those can be manually defined or simply built-in in Cornichon.


## Built-in steps

Cornichon has a set of built-in steps for various HTTP calls and assertions on the response.


### HTTP effects

- GET, DELETE, HEAD, OPTIONS, POST, PUT and PATCH use the same request builder for request's body, URL parameters and headers.

```scala
head("http://superhero.io/daredevil")

get("http://superhero.io/daredevil").withParams(
  "firstParam" → "value1",
  "secondParam" → "value2")

delete("http://superhero.io/daredevil").withHeaders(("Authorization", "Basic QWxhZGRpbjpvcGVuIHNlc2FtZQ=="))

post("http://superhero.io/batman").withBody("JSON description of Batman goes here")

put("http://superhero.io/batman").withBody("JSON description of Batman goes here").withParams(
  "firstParam" → "value1",
  "secondParam" → "value2")

patch("http://superhero.io/batman").withBody("JSON description of Batman goes here")
```


There is a built-in support for HTTP body defined as String, if you wish to use other types please check out the section [Custom HTTP body type](#custom-http-body-type).

### HTTP assertions

- assert response status

```scala
status.is(200)

```

- assert response headers

```scala

headers.name("cache-control").isPresent

headers.contain("cache-control" → "no-cache")

headers.name("cache_control").isAbsent

save_header_value("cache_control" → "my-cache-control-value")

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

Ignored keys and extractors are JsonPaths following the format "a.b.c[int].d"

JsonPath can also be used to only assert part of the response
  
```scala
body.path("city").is("Gotham city")

body.path("hasSuperpowers").is(false)

body.path("publisher.name").is("DC")

body.path("city").containsString("Gotham")

body.path("publisher.foundationYear").is(1934)

body.path("publisher.foundationYear").isPresent

body.path("publisher.foundationMonth").isAbsent

```

If one key of the path contains a "." it has to be wrapped with "`" to notify the parser.

```scala

body.path("`message.en`").isPresent

body.path("`message.fr`").isAbsent

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

### HTTP streams

- Server-Sent-Event.

```scala
When I open_sse(s"http://superhero.io/stream", takeWithin = 1.seconds).withParams("justName" → "true")

Then assert body.asArray.hasSize(2)

Then assert body.is("""
  |   eventType      |    data     |
  | "superhero name" |  "Batman"   |
  | "superhero name" | "Superman"  |
""")
```

SSE streams are aggregated over a period of time in an array, therefore the previous array predicates can be re-used.


### GraphQL support

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

```query_gql``` can also be used for mutation query.


- GraphQL JSON

all built-in steps accepting String input/output can also accept an alternative lightweight JSON format using the ```gql``` StringContext.

```scala
import com.github.agourlay.cornichon.json.CornichonJson._

And assert body.ignoring("city", "publisher").is(
  gql"""
  {
    name: "Batman",
    realName: "Bruce Wayne",
    hasSuperpowers: false
  }
  """)
```



### Session steps

- setting a value in ```session```

```scala
save("favorite-superhero" → "Batman")
```

- saving value to ```session``

```scala
save_body_path("city" -> "batman-city")

```

- asserting value in ```session```

```scala
session_value("favorite-superhero").is("Batman")
```

- asserting JSON value in ```session```

```scala
session_value("my-json-response").asJson.path("a.b.c").ignoring("d").is(...)
```


- asserting existence of value in ```session```

```scala
  session_value("favorite-superhero").isPresent
  session_value("favorite-superhero").isAbsent
````

### Wrapper steps

Wrapper steps allow to control the execution of a series of steps to build more powerful tests.

- repeating a series of ```steps```

```scala
Repeat(3) {
  When I get("http://superhero.io/batman")

  Then assert status.is(200)
}
```

- repeating a series of ```steps``` during a period of time

```scala
RepeatDuring(300.millis) {
  When I get("http://superhero.io/batman")

  Then assert status.is(200)
}
```

- retry a series of ```steps``` until it succeeds or reaches the limit

```scala
RetryMax(3) {
  When I get("http://superhero.io/batman")

  Then assert status.is(200)
}
```


- repeating a series of ```steps``` until it succeeds over a period of time at a specified interval (handy for eventually consistent endpoints)

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

- execute a series of steps 'n' times concurrently and wait 'maxTime' for completion.

```scala
Concurrently(factor = 3, maxTime = 10 seconds) {

  When I get("http://superhero.io/batman")

  Then assert status.is(200)
}

```

- execute a series of steps and fails if the execution does not complete within 'maxDuration'.

```scala
Within(maxDuration = 10 seconds) {

  When I get("http://superhero.io/batman")

  Then assert status.is(200)
}

```

- repeat a series of steps with different inputs specified via a datatable

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
  AssertStep("sum of 'a' + 'b' = 'c'", s ⇒ GenericEqualityAssertion(s.get("a").toInt + s.get("b").toInt, s.get("c").toInt))
```

- WithHeaders automatically sets headers for several steps useful for authenticated scenario.

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

By default this server responds with 201 to any POST request and 200 for all the rest.

Additionally it provides three administrations features:
- fetching recorded received requests
- resetting recorded received requests
- toggling on/off the error mode to return HTTP 500 to incoming requests

The server records all requests received as a JSON array of HTTP request for later assertions.

There are two ways to perform assertions on the server statistics, either by querying the session at the end of the block or by contacting directly the server while it runs.

Refer to those [examples](https://github.com/agourlay/cornichon/blob/master/src/test/scala/com/github/agourlay/cornichon/examples/MockServerExample.scala) for more information.

This feature is experimental and subject to changes.

- Log duration

By default all ```Step``` execution time can be found in the logs, but sometimes one needs to time a series of steps.

This is where ```LogDuration``` comes in handy, it requires a label that will be printed as well to identify results.

```scala
LogDuration(label = "my experiment") {

  When I get("http://superhero.io/batman")

  Then assert status.is(200)
}

```

### Debug steps

- showing session content for debugging purpose

```scala
 And I show_session

 And I show_last_response

 And I show_last_status

 And I show_last_body

 And I show_last_headers
```


Those descriptions might be already outdated, in case of doubt always refer to those [examples](https://github.com/agourlay/cornichon/blob/master/src/test/scala/com/github/agourlay/cornichon/examples/superHeroes/SuperHeroesScenario.scala) as they are executed as part of Cornichon's test suite.

## DSL composition

Series of steps defined with Cornichon's DSL can be reused within different ```Scenarios```.
 
Using the keyword ```Attach``` if the series starts with a ```Step``` and without if it starts with a wrapping bloc.
 
```scala
class CornichonExamplesSpec extends CornichonFeature {

  lazy val feature =
    Feature("Cornichon feature example") {

      Scenario("demonstrate DSL composition") {
    
        Then assert superhero_exists("batman")
    
        Then assert random_superheroes_until("Batman")
    
      }
    }
    
  def superhero_exists(name: String) =
    Attach {
      When I get(s"/superheroes/$name").withParams("sessionId" → "<session-id>")
      Then assert status.is(200)
    }
  
  def random_superheroes_until(name: String) =
    Eventually(maxDuration = 3 seconds, interval = 10 milliseconds) {
      When I get("/superheroes/random").withParams("sessionId" → "<session-id>")
      Then assert body.path("name").is(name)
      Then I print_step("bingo!")
    }  
      
```

It is possible to give a title to an attached bloc using ```AttachAs(title)```.

## Placeholders

Most built-in steps can use placeholders in their arguments, those will be automatically resolved from the ```session```:

- URL
- Expected body
- HTTP params (name and value)
- HTTP headers (name and value)
- JSON Path

```scala
Given I save("favorite-superhero" → "Batman")

Then assert session_value("favorite-superhero").is("Batman")

When I get("http://localhost:8080/superheroes/<favorite-superhero>")

Then assert body.is(
  """
  {
    "name": "<favorite-superhero>",
    "realName": "Bruce Wayne",
    "city": "Gotham city",
    "publisher": "DC"
  }
  """
)

And I save_body_path("city" -> "batman-city")

Then assert session_value("batman-city").is("Gotham city")

Then assert body.is(
  """
  {
    "name": "<favorite-superhero>",
    "realName": "Bruce Wayne",
    "city": "<batman-city>",
    "publisher": "DC"
  }
  """
)

```

It is also possible to inject random values inside placeholders using:

- ```<random-uuid>``` for a random UUID
- ```<random-positive-integer>``` for a random Integer between 0-10000
- ```<random-string>``` for a random String of length 5
- ```<random-alphanum-string>``` for a random alphanumeric String of length 5
- ```<random-boolean>``` for a random Boolean string
- ```<timestamp>``` for the current timestamp

```scala
post("http://url.io/somethingWithAnId").withBody(
"""
  {
    "id" : "<random-uuid>"
  }
""")
```

If you save several times a value under the same key, the ```session``` will behave like a Multimap by appending the values. 

It becomes then possible to retrieve past values :

- ```<name>``` always uses the latest value taken by the key.
- ```<name[0]>``` uses the first value taken by the key
- ```<name[1]>``` uses the second element taken by the key

## Custom steps

### Effects and Assertions

There are two kind of ```step``` :
- EffectStep ```Session => Future[Session]``` : Runs a side effect and populates the ```Session``` with values.
- AssertStep ```Sesssion => Assertion``` : Describes the expectation of the test.

 
A ```session``` is a Map-like object used to propagate state throughout a ```scenario```. It is used to resolve [placeholders](#placeholders) and save the result computations for later assertions.

The test engine is responsible to test the validity of the provided ```Assertion``` which can be one of the following concrete implementations.

- ```GenericEqualityAssertion```:  simply a container for 2 values, the expected value and the actual result, the engine will try its best to provide a meaningful error message.
 
```scala
When I AssertStep("always true!", s => GenericEqualityAssertion(true, true))
```

- ```CustomMessageEqualityAssertion```: similar to the above but with a specific error message.

```scala
 CustomMessageAssertion[A](expected: A, result: A, customMessage: A ⇒ String)
```

The engine will feed the actual result to the ```customMessage``` function.

- ```LessThanAssertion```, ```GreaterThanAssertion``` and ```BetweenAssertion```: provide validation of ordering.


```scala
When I EffectStep(
  title = "estimate PI",
  action = s => s.add("result", piComputation())
)

Then assert AssertStep(
  title = "check estimate",
  action = s => BetweenAssertion(3.1, s.get("result"), 3.2)
)
```

```Assertions``` can also be composed using ```and``` and ```or```, for instance ```BetweenAssertion``` is the result of ```LessThanAssertion``` and ```GreaterThanAssertion```.

This is rather low level therefore you not should write your steps like that directly inside the DSL but hide them behind functions with appropriate names.

Fortunately a bunch of built-in steps and primitive building blocs are already available for you.

Note for advance users: it is also possible to write custom wrapper steps by implementing ```WrapperStep```.


### HTTP service

Sometimes you still want to perform HTTP calls inside of custom effect steps, this is where the ```http``` service comes in handy. 

In order to illustrate its usage let's take the following example, you would like to write a custom step like:

```scala
def feature = Feature("Customer endpoint"){

  Scenario("create customer"){

    When I create_customer

    Then assert status.is(201)

  }
```

Most of the time you will create your own trait containing your custom steps and declare a self-type on ```CornichonFeature``` to be able to access the ```httpService```. 

It exposes a method ```requestEffect``` turning an ```HttpRequest``` into an asynchronous effect.

```scala
trait MySteps {
  this: CornichonFeature ⇒

  def create_customer = EffectStep(
    title = "create new customer",
    effect = http.requestEffect(
      request = HttpRequest.post("/customer").withPayload("someJson"),
      expectedStatus = Some(201)
      extractor = RootExtractor("customer")
    )
  )
}

```

The built-in HTTP steps available on the DSL are actually built on top of the ```httpService``` which means that you benefit from all the existing infrastructure to:

- resolve placeholders in URL, query params, body and headers.
- automatically populate the session with the results of the call such as response body, status and headers (it is also possible to pass a custom extractor).
- handle common errors such as timeout and malformed requests.


## Feature options

To implement a ```CornichonFeature``` it is only required to implement the ```feature``` function. However a number of useful options are available using override.

### Before and after hooks

Hooks are available to set up and tear down things as usual but this feature is not integrated into the DSL.

Four functions are available in ```CornichonFeature``` with self-explanatory names:

Taking ```Unit``` expression

```scala
beforeFeature { // do side effect here }

afterFeature {  // do side effect here }
```

Taking ```Step*``` expression.

```scala
beforeEachScenario ( // feed Step* )

afterEachScenario ( // feed Step* )
```

### Base URL

Instead of repeating at each HTTP statement the full URL, it is possible to set a common URL for the entire ```feature``` by overriding:

```scala
override lazy val baseUrl = s"http://localhost:8080"

```

and then only provide the missing part in the HTTP step definition

```scala
 When I get("/superheroes/Batman")
 
 When I delete("/superheroes/GreenLantern")

```

You can still override the base URL of a single step by providing the complete URL starting with the HTTP protocol.

### Request timeout

The default value for the HTTP request timeout is ```2 seconds```. As always it can be overridden per scenario.

```scala
import scala.concurrent.duration._

override lazy val requestTimeout = 100 millis

```

### Register custom extractors

In some cases it makes sense to declare ```extractors``` to avoid code duplication when dealing with ```session``` values.

An extractor is responsible to describe using a JsonPath how to build a value from an existing value in ```session```.

For instance if most of your JSON responses contain a field ```id``` and you want to use it as a placeholder without always having to manually extract and save the value into the ```session``` you can write :
 
```scala
   override def registerExtractors = Map(
     "response-id" → JsonMapper(HttpService.LastResponseBodyKey, "id")
   )
```

It is now possible to use ```<response-id>``` or ```<response-id[integer]>``` in the steps definitions.

It works for all keys in ```Session```, let's say we also have objects registered under keys ```customer``` & ```product```: 

 
```scala
   override def registerExtractors = Map(
     "response-version" → JsonMapper(HttpService.LastResponseBodyKey, "version"),
     "customer-street" → JsonMapper("customer", "address.street"),
     "product-first-rating" → JsonMapper("product", "rating[0].score")
   )
```


## Execution model

By default the ```features``` are executed sequentially and the ```scenarios``` within are executed in parallel.

This execution is configurable if you have specific constraints.

To run ```scenarios``` sequentially it is necessary to declare in your application.conf file

```scala
cornichon {
  executeScenariosInParallel = false
}
```

To run ```features``` in parallel it is necessary to manually set a flag in your SBT build file.

```scala
parallelExecution in Test := true
```

or through the command line ```sbt test parallelExecution in Test := true```

## Ignoring features or scenarios

```Feature``` or individual ```scenario``` can also be marked to be ignored.

```scala
class CornichonExamplesSpec extends CornichonFeature {

  // Ignore a complete feature
  def feature = Feature("Checking google", ignored = true){
  
    // Ignore a single scenario
    Scenario("Google is up and running", ignored = true){
  
      When I get("http://google.com")
  
      Then assert status.is(302)
    }
  }
}
```


## Custom HTTP body type

By default the HTTP DSL expects a String body but in some cases you might want to work at a higher level of abstraction.

In order to use a custom type as body, it is necessary to provide 3 typeclass instances:

- ```cats.Show``` used to print the values
- ```io.circe.Encoder``` used to convert the values to JSON
- ```com.github.agourlay.cornichon.resolver.Resolvable``` used to provide a custom String representation in which placeholders can be resolved

For instance if you wish to use the ```JsObject``` from ```play-json``` as HTTP request's body you can define the following instances in your code:

```scala

  lazy implicit val jsonResolvableForm = new Resolvable[JsObject] {
    def toResolvableForm(s: JsObject) = s.toString()
    def fromResolvableForm(s: String) = Json.parse(s).as[JsObject]
  }

  lazy implicit val showJson = new Show[JsObject] {
    override def show(f: JsObject): String = f.toString()
  }

  lazy implicit val JsonEncoder:Encoder[JsObject] = new Encoder[JsObject] {
    override def apply(a: JsObject): Json = parse(a.toString()).getOrElse(cJson.Null)
  }

```


## ScalaTest integration

As Cornichon uses Scalatest it is possible to use all the nice CLI from SBT + ScalaTest to trigger tests:

- ```~test``` tilde to re-run a command on change.
- ```testOnly *CornichonExamplesSpec``` to run only the feature CornichonExamplesSpec.
- ```testOnly *CornichonExamplesSpec -- -t "Cornichon feature example should CRUD Feature demo"``` to run only the scenario ```CRUD Feature demo``` from the feature ```Cornichon feature example```. 

The full name of a scenario is ```feature-name should scenario-name```.

See [SBT doc](http://www.scala-sbt.org/0.13/docs/Testing.html) and [ScalaTest doc](http://www.scalatest.org/user_guide/using_the_runner) for more information.

The ```steps``` execution logs will only be shown if:
- the scenario fails
- the scenario succeeded and contains at least one ```DebugStep``` such as ```And I show_last_status```


## SSL configuration

Testing environment often have broken certificates, it is possible to disable hostname verification by adding the following configuration to your reference.conf or application.conf in src/test/resources

```
akka {
  ssl-config{
    loose {
      disableHostnameVerification = true
    }
  }
}
```

## License

**Cornichon** is licensed under [Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0).
