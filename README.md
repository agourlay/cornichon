cornichon [![Build Status](https://travis-ci.org/agourlay/cornichon.png?branch=master)](https://travis-ci.org/agourlay/cornichon)
=========

Scala DSL for testing JSON HTTP API

Work in progress...

See [examples](https://github.com/agourlay/cornichon/blob/master/src/test/scala/com/github/agourlay/cornichon/examples) for usage.

// TODOs
- get rid of colon in step definition
- get rid of the lazy val for featureName
- data table (Json input and HTTP param)
- session is a multimap (adding on the head the last-response-body & offering API to fetch older values)
- provide extractors in body assert (with syntactic sugar if possible)
- string based DSL with parboiled2
- hook Before/After Feature/Scenario
- eventually (repeat action until success n times or during n seconds)
- support Server Sent Event predicate
- improve Scalatest integration, display step progression