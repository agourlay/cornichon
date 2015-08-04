cornichon [![Build Status](https://travis-ci.org/agourlay/cornichon.png?branch=master)](https://travis-ci.org/agourlay/cornichon)
=========

Scala DSL for testing JSON HTTP API

Work in progress...

See [examples](https://github.com/agourlay/cornichon/blob/master/src/test/scala/com/github/agourlay/cornichon/examples) for usage.

// TODOs
- build request abstraction
- call resolver on assertion
- errors with JSON Diff if possible
- JSON assertion ignoring fields
- data table
- session is a multimap (adding on the head the last-response-body & offering API to fetch older values)
- provide extractors in body assert (with syntaxic sugar if possible)
- string based DSL with parboiled2
- hook Before/After Feature
- hook Before/After Scenario
- use infix notation? Given GET() ...
- support Server Sent Event predicate
- improve Scalatest integration, display step progression