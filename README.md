cornichon [![Build Status](https://travis-ci.org/agourlay/cornichon.png?branch=master)](https://travis-ci.org/agourlay/cornichon)
=========

Scala DSL for testing JSON HTTP API

## Status 

WIP - no release yet


## Structure

A feature can have several scenarios which can have several steps.

A feature fails if one or more scenarios fail, a scenario fail if one step fails.


## DSL

Steps starts with either :

- Given I ...
- When I ...
- Then I ...
- Then assert ...
- And I ...
- And assert ...

They do not change the behaviour of the Steps, they are just here for readability.

Generally step definitions with "I" are reserved for steps with side effects, like HTTP calls and definition with "assert" for assertion.


```scala
When I GET("http://localhost:8080/superheroes/Batman")

Then assert status_is(200)

```

## Steps

Cornichon has a set of built-in steps for various HTTP calls and assertions on the response.

- usual GET/POST/UPDATE/DELETE
- assert response body JSON
- assert http status
- assert http headers
- assert JSON array using Data table
- experimental support SSE
- asserting value in Session
- setting a value in session


## Placeholders

Json content assertion can use placeholder that will be resolved from the session.

```scala
 And I save("favorite-superhero" → "Batman")

Then assert session_contains("favorite-superhero" → "Batman")

When I GET("http://localhost:8080/superheroes/<favorite-superhero>")

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

```


## Usage


For more complete examples see the following [file](https://github.com/agourlay/cornichon/blob/master/src/test/scala/com/github/agourlay/cornichon/examples/CornichonExamplesSpec.scala).


## Todos

- abstraction to propagate authentication throughout a scenario
- shortcut to execute a single scenario
- session is a multimap (adding on the head the last-response-body & offering API to fetch older values)
- eventually bloc - repeat action until success during a period of time