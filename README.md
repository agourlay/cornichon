cornichon [![Build Status](https://travis-ci.org/agourlay/cornichon.png?branch=master)](https://travis-ci.org/agourlay/cornichon)
=========

A Scala DSL for testing JSON HTTP API 

Quick example? It looks like [this](https://github.com/agourlay/cornichon/blob/master/src/test/scala/com/github/agourlay/cornichon/examples/CornichonExamplesSpec.scala).

Inside a Scala project, place your Cornichon tests in ```src/test/scala``` and run them using ```sbt test```.

## Status 

WIP - no release yet


## Structure

A Cornichon test is the definition of a so called ```feature```. 

A ```feature``` can have several ```scenarios``` which can have several ```steps```.

In the example below, we have one ```feature``` with one ```scenario``` with two ```steps```.

```scala
class CornichonExamplesSpec extends CornichonFeature {
  def feature = Feature("Checking google")(
  
      Scenario("Google is up and running") { implicit b ⇒
  
        When I GET("http://google.com")
  
        Then assert status_is(200)
      }
  )
}
```

A feature fails - if one or more scenarios fail

A scenario fails - if at a step fails. 

A scenario will stop at the first failed step and ignore the remaining steps.

Check this [section](#implicit-builder) if you wonder what this ```implicit b =>``` thingy is.

## DSL

Steps start with one of the following prefix followed by a step definition :

- Given I
- When I
- Then I
- And I
- Then assert
- And assert
- Then assert_not (expects the step to fail)
- And assert_not (expects the step to fail)

Those prefix do not change the behaviour of the steps, generally step definitions with "I" are reserved for steps with side effects, like HTTP calls and definition with "assert" for assertion.

## Steps

A step is an abstraction describing an action which result can be compared against an expected value. 

todo

## Built-in steps
Cornichon has a set of built-in steps for various HTTP calls and assertions on the response.

- usual GET/POST/UPDATE/DELETE
- assert response body
- assert http status
- assert http headers
- assert JSON array using data table
- asserting value in Session
- setting a value in Session
- repeat a series of Steps
- repeat a series of Steps until it succeed over a period of time at a specified interval
- JSON4s XPath integration (usage of ```\```, ```\\```, ```find```, ```filter```, ```transform``` etc)
- validate response against Json schemas
- experimental support for Server-Sent-Event assertion

## How to build a custom step

todo

## Placeholders

Most built-in steps can use placeholder in their arguments that will be resolved from the session.

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

```


## Usage

Create a test Scala class extending ```CornichonFeature``` and implement the ```feature``` function as presented below.

```scala
class CornichonReadmeExample extends CornichonFeature {

  def feature =
    Feature("Cornichon feature Example")(

      Scenario("Test read demo") { implicit b ⇒
        When I GET("myUrl/superheroes/Batman")

        Then assert status_is(200)

        And assert body_is(
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
    )
}
```

Cornichon is currently integrated with ScalaTest, so you just have to run ```sbt run``` to trigger features execution.

For more examples see the following [file](https://github.com/agourlay/cornichon/blob/master/src/test/scala/com/github/agourlay/cornichon/examples/CornichonExamplesSpec.scala).

## Implicit builder

Cornichon's DSL uses mutation to improve readability. The argument ```implicit b =>``` represent an implicit step builder required to construct a ```scenario```.

Until a better solution is found, do not forget it :)