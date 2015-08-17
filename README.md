cornichon [![Build Status](https://travis-ci.org/agourlay/cornichon.png?branch=master)](https://travis-ci.org/agourlay/cornichon)
=========

Scala DSL for testing JSON HTTP API

## Status 

WIP - no release yet


## Structure

A ```Feature``` can have several ```Scenarios``` which can have several ```Steps```


## Usage

See the following [example](https://github.com/agourlay/cornichon/blob/master/src/test/scala/com/github/agourlay/cornichon/examples/CornichonExamplesSpec.scala) for the current API.


## Todos

- get rid of colon in Seq step definition
- get rid of the lazy val for feature definition
- abstraction to propagate authentication throughout a scenario
- shortcut to execute a single scenario
- session is a multimap (adding on the head the last-response-body & offering API to fetch older values)
- eventually (repeat action until success n times or during n seconds)
- support Server Sent Event predicate