{%
laika.title = Overview
%}

[![Maven Central](https://img.shields.io/maven-central/v/com.github.agourlay/cornichon-core_3)](https://central.sonatype.com/artifact/com.github.agourlay/cornichon-core_3)
[![License](http://img.shields.io/:license-Apache%202-brightgreen.svg)](http://www.apache.org/licenses/LICENSE-2.0.txt)

## How it works

Cornichon tests are built around three core concepts: **steps**, **session**, and **placeholders**.

Steps are the building blocks of a scenario — each step either performs a side effect (like an HTTP request) or asserts an expected state. When a step produces a result, it is stored in the **session**, a key-value store that lives for the duration of a scenario. Values from the session can then be injected into later steps using **placeholders** like `<my-key>`, which are automatically resolved at runtime.

This simple model makes it natural to chain steps together: make a request, save part of the response, and use it in the next request — all without writing boilerplate code.

## Quick example

```scala
import com.github.agourlay.cornichon.CornichonFeature

class SuperHeroesFeature extends CornichonFeature {

  def feature = Feature("Superheroes API") {

    Scenario("retrieve a superhero") {

      When I get("/superheroes/Batman")

      Then assert status.is(200)

      Then assert body.is("""
      {
        "name": "Batman",
        "realName": "Bruce Wayne",
        "city": "Gotham city",
        "publisher": {
          "name": "DC",
          "foundationYear": 1934
        }
      }
      """)

      And assert body.path("publisher.name").is("DC")

      And assert body.path("publisher.foundationYear").isGreaterThan(1900)
    }
  }
}
```

For more examples see:

- [Embedded Superheroes API](https://github.com/agourlay/cornichon/blob/master/cornichon-test-framework/src/test/scala/com/github/agourlay/cornichon/framework/examples/superHeroes/SuperHeroesScenario.scala) — full feature with HTTP CRUD operations, JSON assertions, and matchers.

- [Math Operations](https://github.com/agourlay/cornichon/blob/master/cornichon-test-framework/src/test/scala/com/github/agourlay/cornichon/framework/examples/math/MathScenario.scala) — custom steps with data tables and property-based testing.

- [Common Patterns](common-patterns.md) — recipes for authentication, polling, data-driven tests, and a full worked example.

Ready to get started? Head over to [Installation](installation.md) and then [Basics](basics.md).
