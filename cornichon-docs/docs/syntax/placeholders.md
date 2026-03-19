{%
laika.title = Placeholders
%}

# Placeholders

Most built-in steps can use placeholders in their arguments, those will be automatically resolved from the [session](../dsl/session-steps.md):

- URL
- Expected body
- HTTP params (name and value)
- HTTP headers (name and value)
- JSON Path

```scala mdoc:silent
import com.github.agourlay.cornichon.CornichonFeature

class PlaceholderFeature extends CornichonFeature {

  def feature =
  Feature("Placeholders examples") {

    Scenario("abstract favorite superheroes") {

      Given I save("favorite-superhero" -> "Batman")

      Then assert session_value("favorite-superhero").is("Batman")

      When I get("http://localhost:8080/superheroes/<favorite-superhero>")

      Then assert body.is(
        """
        {
          "name": "<favorite-superhero>",
          "realName": "Bruce Wayne",
          "city": "Gotham city",
          "publisher": "DC"
        }
        """
      )

      And I save_body_path("city" -> "batman-city")

      Then assert session_value("batman-city").is("Gotham city")

      Then assert body.is(
        """
        {
          "name": "<favorite-superhero>",
          "realName": "Bruce Wayne",
          "city": "<batman-city>",
          "publisher": "DC"
        }
        """
      )
    }
  }
}
```

It is also possible to inject random values inside placeholders using:

- `<random-uuid>` for a random UUID (e.g. `f47ac10b-58cc-4372-a567-0e02b2c3d479`)
- `<random-positive-integer>` for a random Integer between 0 and 9999
- `<random-string>` for a random String of 5 characters
- `<random-alphanum-string>` for a random alphanumeric String of 5 characters
- `<random-boolean>` for a random Boolean string (`true` or `false`)
- `<random-timestamp>` for a random timestamp in seconds (Unix epoch)
- `<current-timestamp>` for the current timestamp in seconds (Unix epoch)
- `<scenario-unique-number>` for a unique number scoped per scenario
- `<global-unique-number>` for a globally unique number across all features

```scala
post("http://url.io/somethingWithAnId").withBody(
"""
  {
    "id" : "<random-uuid>"
  }
""")
```

If you save several times a value under the same key, the `session` will behave like a Multimap by appending the values.

It becomes then possible to retrieve past values:

- `<name>` always uses the latest value taken by the key.
- `<name[0]>` uses the first value taken by the key
- `<name[1]>` uses the second element taken by the key

## Custom extractors

In some cases it makes sense to declare `extractors` to avoid code duplication when dealing with [session](../dsl/session-steps.md) values. Once registered, they can be used as `<extractor-name>` in any step.

An extractor describes using a JsonPath how to build a value from an existing value in the session.

For instance if most of your JSON responses contain a field `id` and you want to use it as a placeholder without always having to manually extract and save the value:

```scala
   override def registerExtractors = Map(
     "response-id" -> JsonMapper(HttpService.SessionKeys.lastResponseBodyKey, "id")
   )
```

It is now possible to use `<response-id>` or `<response-id[integer]>` in the steps definitions.

It works for all keys in `Session`, let's say we also have objects registered under keys `customer` & `product`:

```scala
   override def registerExtractors = Map(
     "response-version" -> JsonMapper(HttpService.SessionKeys.lastResponseBodyKey, "version"),
     "customer-street" -> JsonMapper("customer", "address.street"),
     "product-first-rating" -> JsonMapper("product", "rating[0].score")
   )
```

### Other mapper types

In addition to `JsonMapper`, several other mapper types are available for custom extractors:

- `SimpleMapper` generates a static value

```scala
"build-number" -> SimpleMapper(() => BuildInfo.version)
```

- `TextMapper` extracts a session value with an optional transformation

```scala
"uppercased-name" -> TextMapper("name", _.toUpperCase)
```

- `SessionMapper` extracts a value from session with error handling

```scala
"full-name" -> SessionMapper(s =>
  for {
    first <- s.get("first-name")
    last <- s.get("last-name")
  } yield s"$first $last"
)
```

- `RandomMapper` generates a value using the `RandomContext` for reproducibility

```scala
"random-city" -> RandomMapper(rc =>
  List("Gotham", "Metropolis", "Star City")(rc.nextInt(3))
)
```

- `HistoryMapper` extracts from the full history of values for a session key

```scala
"visit-count" -> HistoryMapper("visited-pages", history => history.size.toString)
```
