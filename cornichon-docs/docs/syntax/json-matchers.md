{%
laika.title = JSON matchers
%}

# JSON matchers

If the exact value of a field is unknown, you can use JSON matchers to make sure it has a certain property or shape.

JSON matchers work more or less like [placeholders](placeholders.md) in practice.

```scala
And assert body.ignoring("city", "realName", "publisher.location").is(
  """
  {
    "name": "<favorite-superhero>",
    "hasSuperpowers": *any-boolean*,
    "publisher": {
      "name": *any-string*,
      "foundationYear": *any-positive-integer*
    }
  }
  """
)
```

You just need to replace the value of the field by one of the built-in JSON matchers *without* quotes.

Here are the available matchers:

- `*is-present*` : checks if the field is defined and not null
- `*is-null*` : checks if the field is null
- `*any-string*` : checks if the field is a String
- `*any-array*` : checks if the field is an Array
- `*any-object*` : checks if the field is an Object
- `*any-number*` : checks if the field is a number (integer or decimal)
- `*any-integer*` : checks if the field is an Integer
- `*any-positive-integer*` : checks if the field is a positive Integer
- `*any-negative-integer*` : checks if the field is a negative Integer
- `*any-uuid*` : checks if the field is a valid UUID
- `*any-boolean*` : checks if the field is a boolean
- `*any-alphanum-string*` : checks if the field is an alphanumeric String
- `*any-date*` : checks if the field is a 'yyyy-MM-dd' date
- `*any-date-time*` : checks if the field is a `yyyy-MM-dd'T'HH:mm:ss.SSS'Z'` datetime
- `*any-time*` : checks if the field is a 'HH:mm:ss.SSS' time

@:callout(warning)
Matchers are not supported for JSON array assertions via `asArray`.
@:@

## Custom matchers

You can register your own matchers by overriding `registerMatchers` in your feature class. A `Matcher` takes a key (used as `*key*` in assertions), a description, and a predicate on `io.circe.Json`:

```scala
import com.github.agourlay.cornichon.CornichonFeature
import com.github.agourlay.cornichon.matchers.Matcher
import io.circe.Json

class MyFeature extends CornichonFeature {

  override def registerMatchers: List[Matcher] = List(
    Matcher(
      key = "any-email",
      description = "checks if the field is a valid email address",
      predicate = _.asString.exists(s => s.contains("@") && s.contains("."))
    ),
    Matcher(
      key = "any-positive-number",
      description = "checks if the field is a positive number",
      predicate = _.asNumber.exists(n => n.toDouble > 0)
    )
  )

  def feature = Feature("My API") {
    Scenario("check user response") {
      When I get("/users/1")
      Then assert body.is("""
      {
        "name": *any-string*,
        "email": *any-email*,
        "score": *any-positive-number*
      }
      """)
    }
  }
}
```

Custom matchers follow the same `*key*` syntax as built-in matchers and can be mixed freely with them.
