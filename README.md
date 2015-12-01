cornichon [![Build Status](https://travis-ci.org/agourlay/cornichon.png?branch=master)](https://travis-ci.org/agourlay/cornichon) [![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.github.agourlay/cornichon_2.11/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.github.agourlay/cornichon_2.11) [![License](http://img.shields.io/:license-Apache%202-brightgreen.svg)](http://www.apache.org/licenses/LICENSE-2.0.txt)
=========

[![Join the chat at https://gitter.im/agourlay/cornichon](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/agourlay/cornichon?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

An extensible Scala DSL for testing JSON HTTP APIs.

## Installation

``` scala
libraryDependencies += "com.github.agourlay" %% "cornichon" % "0.2.4" % "test"
```

## Quick overview

Create a class extending ```CornichonFeature``` and implement the ```feature``` function as presented below.

```scala

class ReadmeExample extends CornichonFeature {

  def feature = Feature("OpenMovieDatabase API"){ implicit a ⇒

      Scenario("list GOT season 1 episodes"){ implicit b =>
      
          When I GET("http://www.omdbapi.com", params = "t" -> "Game of Thrones", "Season" -> "1")
    
          Then assert status_is(200)
    
          And assert body_is("""
            {
              "Title": "Game of Thrones",
              "Season": "1"
            }
            """, ignoring = "Episodes", "Response")
    
    
          And assert body_is(_ \ "Episodes",
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
    
          And assert body_array_size_is(_ \ "Episodes", 10)
    
          And assert body_is(b => (b \ "Episodes")(0),
            """
            {
              "Title": "Winter Is Coming",
              "Released": "2011-04-17",
              "Episode": "1",
              "imdbRating": "8.1",
              "imdbID": "tt1480055"
            }
            """)
    
          And assert body_is(b => (b \ "Episodes")(0) \ "Released", "2011-04-17")
    
          And assert body_array_contains(_ \ "Episodes", 
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

Cornichon is currently integrated with [ScalaTest](http://www.scalatest.org/), so place your ```Feature``` inside ```src/test/scala``` and run them using ```sbt test```.

For more examples see the following files which are part of the test pipeline:

- [OpenMovieDatabase API](https://github.com/agourlay/cornichon/blob/master/src/it/scala/com/github/agourlay/cornichon/examples/OpenMovieDatabase.scala).

- [Embedded Superheroes API](https://github.com/agourlay/cornichon/blob/master/src/test/scala/com/github/agourlay/cornichon/examples/CornichonExamplesSpec.scala).


## Structure

A Cornichon test is the definition of a so-called ```feature```. 

A ```feature``` can have several ```scenarios``` which can have several ```steps```.

In the example below, we have one ```feature``` with one ```scenario``` with two ```steps```.

```scala
class CornichonExamplesSpec extends CornichonFeature {

  def feature = Feature("Checking google"){ implicit scenarioBuilder ⇒
  
      Scenario("Google is up and running"){ implicit stepBuilder ⇒
  
          When I GET("http://google.com")
  
          Then assert status_is(302)
      }
  }
}
```

A ```feature``` fails - if one or more ```scenarios``` fail.

A ```scenario``` fails - if at least one ```step``` fails.

A ```scenario``` will stop at the first failed step encountered and ignore the remaining ```steps```.

Check this [section](#implicit-builder) if you wonder what those ```implicit _ =>``` are.

## DSL

Statements start with one of the prefixes below followed by a ```step``` definition :

- Given I | a
- When I | a
- And I | a | assert | assert_not (expects the step to fail)
- Then I | a | assert | assert_not (expects the step to fail)

Those prefixes do not change the behaviour of the steps.

First run a ```step``` with a side effect or a result then assert its value in a second ```step```.

## Built-in steps

Cornichon has a set of built-in steps for various HTTP calls and assertions on the response.


- GET and DELETE share the same signature

 (url, optional params String tuples*)(optional tuple headers Seq)

```scala
GET("http://superhero.io/daredevil")

GET("http://superhero.io/daredevil", params = "firstParam" → "value1", "secondParam" → "value2")

DELETE("http://superhero.io/daredevil")(headers = Seq(("Authorization", "Basic QWxhZGRpbjpvcGVuIHNlc2FtZQ==")))
```

- POST and UPDATE share the same signature

 (url, payload as String, optional params String tuples*)(optional tuple headers Seq)

```scala
POST("http://superhero.io/batman", payload = "JSON description of Batman goes here")

PUT("http://superhero.io/batman", payload = "JSON description of Batman goes here", params = "firstParam" → "value1", "secondParam" → "value2")

POST("http://superhero.io/batman", payload = "JSON description of Batman goes here")(headers = Seq(("Authorization", "Basic QWxhZGRpbjpvcGVuIHNlc2FtZQ==")))
```

- assert response status

```scala
status_is(200)

```

- assert response headers

```scala
headers_contain("cache-control" → "no-cache")

```

- assert response body comes with different flavours (ignoringKeys, whiteList))

```scala
body_is(
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

body_is(
  """
  {
    "name": "Batman",
    "realName": "Bruce Wayne"
  }
  """, ignoring = "city", "hasSuperpowers", "publisher")

body_is(whiteList = true, expected = """
  {
    "name": "Batman",
    "realName": "Bruce Wayne"
  }
  """)
```

It also possible to use [Json4s XPath](http://json4s.org/#xpath--hofs) extractors
  
```scala
body_is(_ \ "city", "Gotham city")

body_is(_ \ "hasSuperpowers", false)

body_is(_ \ "publisher" \ "name", "DC")

body_is(_ \ "publisher" \ "foundationYear", 1934)

```

If the endpoint returns a collection assert response body has several options (ordered, ignoring and using data table)

```scala
body_is(ordered = true,
  """
  [{
    "name": "Batman",
    "realName": "Bruce Wayne"
  },
  {
    "name": "Superman",
    "realName": "Clark Kent"
  }]
  """, ignoring = "city", "hasSuperpowers", "publisher")

body_is(ordered = false,
  """
  [{
    "name": "Superman",
    "realName": "Clark Kent"
  },
  {
    "name": "Batman",
    "realName": "Bruce Wayne"
  }]
  """, ignoring = "city", "hasSuperpowers", "publisher")
  
body_is(ordered = true, expected = """
  |    name     |    realName    |     city      |  hasSuperpowers |
  | "Batman"    | "Bruce Wayne"  | "Gotham city" |      false      |
  | "Superman"  | "Clark Kent"   | "Metropolis"  |      true       |
""", ignoring = "publisher")  
  
body_array_size_is(2)
  
body_array_contains("""
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

body_array_size_is and response_array_contains have also the possibility to accept an extra first argument (a root key or a JValue extractor) to work on a nested collection.

- setting a value in ```session```

```scala
save("favorite-superhero" → "Batman")
```

- saving value to ```session``

```scala
save_body_key("city", "batman-city")

save_from_body(_ \ "city", "batman-city")

```

- asserting value in ```session```

```scala
session_contains("favorite-superhero" → "Batman")
```

- showing sessing content for debugging purpose

```scala
 And debug show_session

 And debug show_last_status

 And debug show_last_response_body

 And debug show_last_response_headers
```

- repeating a series of ```steps``` (can be nested)

```scala
Repeat(3) {
  When I GET("http://superhero.io/batman")

  Then assert status_is(200)
}
```

- repeating a series of ```steps``` until it succeeds over a period of time at a specified interval (handy for eventually consistent endpoints)

```scala
Eventually(maxDuration = 15.seconds, interval = 200.milliseconds) {

    When I GET("http://superhero.io/random")

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
```

- execute a series of steps 'n' times concurrently and wait 'maxTime' for completion.

```scala
Concurrently(factor = 3, maxTime = 10 seconds) {

  When I GET("http://superhero.io/batman")

  Then assert status_is(200)
}

```

- WithHeaders automatically sets headers for several steps useful for authenticated scenario.

```scala
WithHeaders(("Authorization", "Basic QWxhZGRpbjpvcGVuIHNlc2FtZQ==")){
  When I GET("http://superhero.io/secured")
  When I GET("http://superhero.io/secured")
}

```

- experimental support for Server-Sent-Event.
 
 SSE streams are aggregated over a period of time in an array, therefore the previous array predicates can be re-used.

```scala
When I GET_SSE(s"http://superhero.io/stream", takeWithin = 1.seconds, params = "justName" → "true")

Then assert response_array_size_is(2)

Then assert body_is("""
  |   eventType      |    data     |
  | "superhero name" |  "Batman"   |
  | "superhero name" | "Superman"  |
""")
```

Those descriptions might be already outdated, in case of doubt always refer to these [examples](https://github.com/agourlay/cornichon/blob/master/src/test/scala/com/github/agourlay/cornichon/examples/CornichonExamplesSpec.scala) as they are executed as part of Cornichon's test suite.

## Custom steps

A ```step``` is an abstraction describing an action which is a function turning a ```Session``` into a result, a new ```Session``` and an expected result value.

In terms of Scala data type it is

```scala
case class ExecutableStep[A](
  title: String,
  action: Session ⇒ (Session, StepAssertion[A])
)
```

A ```step``` action can access and return a modified ```session``` object and a ```StepAssertion```.
 
A ```session``` is a Map-like object used to propagate state throughout a ```scenario```. It is used to resolve [placeholders](#placeholders)

A ```StepAssertion``` is simply a container for 2 values, the expected value and the actual result. The test engine is responsible to test the equality of the ```StepAssertion``` values.
 
The engine will try its best to provide a meaningful error message, if a specific error message is required tt is also possible to provide a custom error message using a ```DetailedStepAssertion```.

```scala
 DetailedStepAssertion[A](expected: A, result: A, details: A ⇒ String)
```

The engine will feed the actual reasult to the ```details``` function.

In practice the simplest executable statement in the DSL is

```scala
When I ExecutableStep("do nothing", s => (s, StepAssertion(true, true)))
```

Let's try to assert the result of a computation

```scala
When I ExecutableStep("calculate", s => (s, StepAssertion(2 + 2, 4)))
```

The ```session``` is used to store the result of a computation in order to reuse it or to apply more advanced assertions on it later.


```scala
When I ExecutableStep(
  title = "run crazy computation",
  action = s => {
    val res = crazy-computation()
    (s.add("result", res.infos),StepAssertion(res.isSuccess, true))
  })

Then assert ExecutableStep(
  title = "check computation infos",
  action = s => {
    val resInfos = s.get("result")
    (s, StepAssertion(resInfos, "Everything is fine"))
  })
```

This is extremely low level and you not should write your steps like that directly inside the DSL.

Fortunately a bunch of built-in steps and primitive building blocs are already available.

Most of the time you will create your own trait containing your custom steps :

```scala
trait MySteps {
  this: CornichonFeature ⇒

  // here access all the goodies from the DSLs and the HttpService.
}

```

## Placeholders

Most built-in steps can use placeholders in their arguments, those will be automatically resolved from the ```session```:

- Url
- Expected body
- HTTP params (name and value)
- HTTP headers (name and value)

```scala
Given I save("favorite-superhero" → "Batman")

Then assert session_contains("favorite-superhero" → "Batman")

When I GET("http://localhost:8080/superheroes/<favorite-superhero>")

Then assert body_is(
  """
  {
    "name": "<favorite-superhero>",
    "realName": "Bruce Wayne",
    "city": "Gotham city",
    "publisher": "DC"
  }
  """
)

And I save_body_key("city", "batman-city")

Then assert session_contains("batman-city" → "Gotham city")

Then assert body_is(
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
- ```<random-positive-integer>``` for a random Integer between 0-100
- ```<random-string>``` for a random String of length 5
- ```<random-boolean>``` for a random Boolean string
- ```<timestamp>``` for the current timestamp

```scala
POST("http://url.io/somethingWithAnId", payload = """
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

Taking ```Seq[Step]``` expression.

```scala
beforeEachScenario { // feed Seq[Step] }

afterEachScenario { // feed Seq[Step] }
```

### Base URL

Instead of repeating at each HTTP statement the full URL, it is possible to set a common URL for the entire ```feature``` by overriding:

```scala
override lazy val baseUrl = s"http://localhost:8080"

```

and then only provide the missing part in the HTTP step definition

```scala
 When I GET("/superheroes/Batman")
 
 When I POST("/superheroes", payload ="")
 
 When I DELETE("/superheroes/GreenLantern")

```

### Request timeout

The default value for the HTTP request timeout is ```2 seconds```. As always it can be overriden per scenario.

```scala
import scala.concurrent.duration._

override lazy val requestTimeout = 100 millis

```

### Register custom extractors

In some cases it makes sense to declare ```extractors``` to avoid code duplication when dealing with ```session``` values.

An extractor is responsible to describe how to build a value from an existing value in ```session```.

For instance if most of your JSON responses contain a field ```id``` and you want to use it as a placeholder without always having to manually extract and save the value into the ```session``` you can write :
 
```scala
   override def registerExtractors = Map(
     "response-id" → Mapper(HttpService.LastResponseBodyKey, v ⇒ (parse(v) \ "id").values.toString)
   )
```

It is now possible to use ```<response-id>``` or ```<response-id[integer]>``` in the steps definitions.

It works for all keys in ```Session```, let's say we also have objects registered under keys ```customer``` & ```product```: 

 
```scala
   override def registerExtractors = Map(
     "response-id" → Mapper(HttpService.LastResponseBodyKey, v ⇒ (parse(v) \ "id").values.toString),
     "customer-id" → Mapper("customer", v ⇒ (parse(v) \ "id").values.toString),
     "product-id" → Mapper("product", v ⇒ (parse(v) \ "id").values.toString)
   )
```


## Execution model

By default the scenarios are executed in parallel.

To disable this behaviour it is necessary to manually set a flag in your SBT build file.

```scala
parallelExecution in Test := false
```

or through the command line ```sbt test parallelExecution in Test := false```

## ScalaTest integration

As Cornichon uses Scalatest it is possible to use all the nice CLI from SBT + ScalaTest to trigger tests:

- ```~test``` tilde to re-run a command on change.
- ```testOnly *CornichonExamplesSpec``` to run only the feature CornichonExamplesSpec.
- ```testOnly *CornichonExamplesSpec -- -t "Cornichon feature example should CRUD Feature demo"``` to run only the scenario ```CRUD Feature demo``` from the feature ```Cornichon feature example```. 

The full name of a scenario is ```feature-name should scenario-name```.

See [SBT doc](http://www.scala-sbt.org/0.13/docs/Testing.html) and [ScalaTest doc](http://www.scalatest.org/user_guide/using_the_runner) for more information.

The ```steps``` execution logs will only be shown if:
- the scenario fails
- the scenario succeeded and contains at least one debug step such as ```And debug show_last_status```


## Implicit builder

In order to have a simple DSL Cornichon uses mutation to build a ```feature```. The arguments ```implicit _ =>``` represent implicit builders required to build the underlying data structure.

Until a better solution is implemented, do not forget those :)