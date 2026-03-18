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
import scala.concurrent.duration._

class ProductsFeature extends CornichonFeature {

  def feature = Feature("Products API") {

    Scenario("create and retrieve a product") {

      // Create
      Given I post("/products").withBody("""{ "name": "Widget", "price": 42 }""")
      Then assert status.is(201)
      And I save_body_path("id" -> "product-id")

      // Retrieve using the saved ID
      When I get("/products/<product-id>")
      Then assert status.is(200)
      Then assert body.path("name").is("Widget")
      Then assert body.path("price").is(42)
    }

    Scenario("search is eventually consistent") {

      Given I post("/products").withBody("""{ "name": "Gadget" }""")
      Then assert status.is(201)

      Eventually(maxDuration = 5.seconds, interval = 200.millis) {
        When I get("/products/search").withParams("q" -> "Gadget")
        Then assert body.asArray.isNotEmpty
      }
    }
  }
}
```

This example shows the key concepts: HTTP requests, JSON body assertions, saving values with `save_body_path`, reusing them via `<product-id>` placeholders, and polling with `Eventually`.

For more examples see:

- [Embedded Superheroes API](https://github.com/agourlay/cornichon/blob/master/cornichon-test-framework/src/test/scala/com/github/agourlay/cornichon/framework/examples/superHeroes/SuperHeroesScenario.scala) — full feature with HTTP CRUD operations, JSON assertions, and matchers.

- [Math Operations](https://github.com/agourlay/cornichon/blob/master/cornichon-test-framework/src/test/scala/com/github/agourlay/cornichon/framework/examples/math/MathScenario.scala) — custom steps with data tables and property-based testing.

- [Common Patterns](common-patterns.md) — recipes for authentication, polling, data-driven tests, and a full worked example.

Ready to get started? Head over to [Installation](installation.md) and then [Basics](basics.md).
