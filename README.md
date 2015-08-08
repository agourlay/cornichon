cornichon [![Build Status](https://travis-ci.org/agourlay/cornichon.png?branch=master)](https://travis-ci.org/agourlay/cornichon)
=========

Scala DSL for testing JSON HTTP API

## Status 

WIP - no release yet

## Usage

See the following [example](https://github.com/agourlay/cornichon/blob/master/src/test/scala/com/github/agourlay/cornichon/examples/CornichonExamplesSpec.scala) for the current API.

## Todos

- get rid of colon in Seq step definition
- get rid of the lazy val for feature definition
- data table (Json input and HTTP param)
- session is a multimap (adding on the head the last-response-body & offering API to fetch older values)
- provide extractors in body assert (with syntactic sugar if possible)
- hook Before/After Feature/Scenario
- eventually (repeat action until success n times or during n seconds)
- support Server Sent Event predicate
- improve Scalatest integration, display step progression