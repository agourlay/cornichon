{%
laika.title = Common patterns
%}

# Common patterns

This page collects recipes for frequently encountered testing scenarios. Each pattern is self-contained and ready to adapt to your own tests.

## Authenticated workflows

Use `WithBasicAuth` or `WithHeaders` to set authentication headers across a block of steps. The headers are automatically cleaned up after the block.

```scala
WithBasicAuth("admin", "secret") {
  When I get("/admin/users")
  Then assert status.is(200)
  Then assert body.asArray.isNotEmpty
}

// Or with a bearer token
WithHeaders(("Authorization", "Bearer <auth-token>")) {
  When I get("/api/protected-resource")
  Then assert status.is(200)
}
```

For a login flow that saves a token for later use:

```scala
Given I post("/auth/login").withBody(
  """
  {
    "username": "admin",
    "password": "secret"
  }
  """)
Then assert status.is(200)
And I save_body_path("token" -> "auth-token")

// Now use the token in subsequent requests
WithHeaders(("Authorization", "Bearer <auth-token>")) {
  When I get("/api/me")
  Then assert status.is(200)
}
```

## Polling eventually-consistent endpoints

Use `Eventually` to retry assertions until they succeed or a timeout is reached. This is ideal for testing systems where changes propagate asynchronously.

```scala
// Create a resource
Given I post("/products").withBody("""{ "name": "Widget" }""")
Then assert status.is(201)
And I save_body_path("id" -> "product-id")

// Wait for it to appear in the search index
Eventually(maxDuration = 10.seconds, interval = 200.millis) {
  When I get("/products/search").withParams("q" -> "Widget")
  Then assert status.is(200)
  Then assert body.asArray.isNotEmpty
}
```

@:callout(info)
Choose `interval` carefully — too short floods the server, too long wastes time. 100-500ms is usually a good range.
@:@

## Data-driven tests

Use `WithDataInputs` to run the same assertions across multiple input sets without duplicating steps.

```scala
WithDataInputs(
  """
    | endpoint             | expected_status |
    | "/health"            | "200"           |
    | "/api/version"       | "200"           |
    | "/does-not-exist"    | "404"           |
  """
) {
  When I get("<endpoint>")
  Then assert status.is("<expected_status>")
}
```

For JSON-formatted inputs, use `WithJsonDataInputs`:

```scala
WithJsonDataInputs(
  """
  [
    { "name": "Batman",   "city": "Gotham" },
    { "name": "Superman", "city": "Metropolis" }
  ]
  """
) {
  When I get("/superheroes/<name>")
  Then assert status.is(200)
  Then assert body.path("city").is("<city>")
}
```

## Shared setup and teardown

Use `beforeEachScenario` and `afterEachScenario` to share common setup across all scenarios in a feature.

```scala
class ApiFeature extends CornichonFeature {

  override lazy val baseUrl = "http://localhost:8080"

  beforeEachScenario {
    Attach {
      Given I post("/auth/login").withBody("""{ "user": "test", "pass": "test" }""")
      And I save_body_path("token" -> "auth-token")
    }
  }

  afterEachScenario {
    Attach {
      Given I post("/auth/logout")
    }
  }

  def feature = Feature("API tests") {
    Scenario("list users") {
      // auth-token is already in session
      WithHeaders(("Authorization", "Bearer <auth-token>")) {
        When I get("/users")
        Then assert status.is(200)
      }
    }
  }
}
```

See [Feature Options](feature-options.md#before-and-after-hooks) for more details on hooks.

## CRUD workflow

A typical create-read-update-delete flow saving IDs between steps:

```scala
// Create
Given I post("/products").withBody("""{ "name": "Widget", "price": 42 }""")
Then assert status.is(201)
And I save_body_path("id" -> "product-id")

// Read
When I get("/products/<product-id>")
Then assert status.is(200)
Then assert body.path("name").is("Widget")

// Update
Given I put("/products/<product-id>").withBody("""{ "name": "Widget Pro", "price": 99 }""")
Then assert status.is(200)

// Verify update
When I get("/products/<product-id>")
Then assert body.path("name").is("Widget Pro")
Then assert body.path("price").is(99)

// Delete
Given I delete("/products/<product-id>")
Then assert status.is(200)

// Verify deletion
When I get("/products/<product-id>")
Then assert status.is(404)
```

## Reusable step blocks

Extract common step sequences into functions using `Attach` for reuse across scenarios.

```scala
def create_superhero(name: String, city: String) =
  Attach {
    Given I post("/superheroes").withBody(
      s"""{ "name": "$name", "city": "$city" }""")
    Then assert status.is(201)
    And I save_body_path("id" -> "hero-id")
  }

def verify_superhero(name: String) =
  Attach {
    When I get("/superheroes/<hero-id>")
    Then assert status.is(200)
    Then assert body.path("name").is(name)
  }

// Usage in scenarios
Scenario("create and verify") {
  Given assert create_superhero("Batman", "Gotham")
  Then assert verify_superhero("Batman")
}
```

Use `AttachAs("title")` to give the block a descriptive name that shows up in the test output.

See [DSL Composition](dsl/utility-steps.md#dsl-composition) for more on reusing steps.

## Iterating over a collection

Use `RepeatWith` or `RepeatFrom` to run steps for each element in a collection, with the current element available as a [placeholder](placeholders.md).

```scala
RepeatWith("Batman", "Superman", "Spiderman")("hero") {
  When I get("/superheroes/<hero>")
  Then assert status.is(200)
}
```

To track the iteration index:

```scala
Repeat(5, "i") {
  When I get("/items/<i>")
  Then assert status.is(200)
}
```

## Concurrent requests

Use `RepeatConcurrently` to load-test an endpoint or verify thread safety:

```scala
RepeatConcurrently(times = 50, parallelism = 10, maxTime = 30.seconds) {
  When I get("/api/health")
  Then assert status.is(200)
}
```

Use `Concurrently` when each step is different:

```scala
Concurrently(maxTime = 10.seconds) {
  When I get("/api/users")
  When I get("/api/products")
  When I get("/api/orders")
}
```

## Full worked example

This example tests a Deck of Cards API, combining several cornichon features: JSON assertions with `ignoring`, `save_body_path` for state extraction, `Eventually` for retrying until a condition is met, `Repeat` for looping, `WithDataInputs` for table-driven tests, and custom assertion steps.

```scala
import com.github.agourlay.cornichon.CornichonFeature
import com.github.agourlay.cornichon.steps.regular.assertStep._
import scala.concurrent.duration._

class DeckOfCard extends CornichonFeature {

  override lazy val baseUrl = "https://deckofcardsapi.com/api"

  def feature =
    Feature("Deck of Card API") {

      Scenario("draw any king") {

        Given I get("/deck/new/shuffle/").withParams("deck_count" -> "1")

        Then assert status.is(200)

        And assert body.ignoring("deck_id").is("""
        {
          "success": true,
          "shuffled": true,
          "remaining": 52
        }
        """)

        And I save_body_path("deck_id" -> "deck-id")

        Eventually(maxDuration = 10.seconds, interval = 10.millis) {
          When I get("/deck/<deck-id>/draw/")
          And assert status.is(200)
          Then assert body.path("cards[0].value").is("KING")
        }
      }

      Scenario("partial deck") {

        Given I get("/deck/new/shuffle/").withParams(
          "cards" -> "AS,2S,KS,AD,2D,KD,AC,2C,KC,AH,2H,KH"
        )

        Then assert status.is(200)

        And I save_body_path("deck_id" -> "deck-id")

        Repeat(6) {
          When I get("/deck/<deck-id>/draw/").withParams("count" -> "2")
          And assert status.is(200)
          Then assert body.path("cards").asArray.not_contains("QH")
        }
      }

      Scenario("test simplified blackjack scoring") {

        WithDataInputs("""
          | c1     | c2      | score |
          | "1"    | "3"     |   4   |
          | "1"    | "KING"  |   11  |
          | "JACK" | "QUEEN" |   20  |
          | "ACE"  | "KING"  |   21  |
        """) {
          Then assert AssertStep(
            title = "value of 'c1' with 'c2' is 'score'",
            action = sc =>
              Assertion.either {
                for {
                  score <- sc.session.get("score").map(_.toInt)
                  c1 <- sc.session.get("c1")
                  c2 <- sc.session.get("c2")
                } yield GenericEqualityAssertion(score, scoreCards(c1) + scoreCards(c2))
              }
          )
        }
      }
    }

  def scoreCards(c: String): Int = c match {
    case "ACE"                          => 11
    case "JACK" | "QUEEN" | "KING"      => 10
    case n                              => n.toInt
  }
}
```
