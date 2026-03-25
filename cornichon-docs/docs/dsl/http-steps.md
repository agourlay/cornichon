{%
laika.title = HTTP steps
%}

# HTTP steps

Cornichon has a set of built-in steps for various HTTP calls and assertions on the response.


## HTTP effects

- GET, DELETE, HEAD, OPTIONS, POST, PUT, and PATCH use the same request builder for the request body, URL parameters, and headers.

```scala
head("http://superhero.io/daredevil")

get("http://superhero.io/daredevil").withParams(
  "firstParam" -> "value1",
  "secondParam" -> "value2")

delete("http://superhero.io/daredevil").withHeaders(("Authorization", "Basic QWxhZGRpbjpvcGVuIHNlc2FtZQ=="))

post("http://superhero.io/batman").withBody("JSON description of Batman goes here")

put("http://superhero.io/batman").withBody("JSON description of Batman goes here").withParams(
  "firstParam" -> "value1",
  "secondParam" -> "value2")

patch("http://superhero.io/batman").withBody("JSON description of Batman goes here")
```

Use `addParams` and `addHeaders` to append to existing parameters and headers instead of replacing them.

```scala
get("http://superhero.io/batman")
  .withParams("a" -> "1")
  .addParams("b" -> "2")
  .addHeaders(("X-Custom", "value"))
```

There is built-in support for HTTP bodies defined as `String`. If you wish to use other types, see [Custom HTTP body type](#custom-http-body-type).

## Session keys from HTTP responses

After every HTTP request, cornichon automatically saves the response into the [session](../dsl/session-steps.md) under these keys:

| Session key | Content | Accessed via |
|---|---|---|
| `last-response-status` | HTTP status code (e.g. `200`) | `status.is(200)` |
| `last-response-body` | Response body as a string | `body.is(...)`, `body.path(...)` |
| `last-response-headers` | Response headers (encoded) | `headers.name(...)`, `headers.contain(...)` |
| `last-response-request` | Description of the request that produced this response | — |

These keys are overwritten on each HTTP request, so the `status`, `body`, and `headers` assertions always refer to the most recent response.

### Saving values from the response

Use `save_body_path` to extract a value from the response body using a JSON path and store it in the session under a custom key:

```scala
And I save_body_path("id" -> "product-id")
And I save_body_path("address.city" -> "city", "address.zip" -> "zip")
```

The saved values are then available as [placeholders](../syntax/placeholders.md): `<product-id>`, `<city>`, etc.

Other save steps:

```scala
// Save the entire response body under a custom key
And I save_body("full-response")

// Save a response header value
And I save_header_value("Content-Type" -> "response-content-type")
```

## HTTP assertions

- assert response status

```scala
status.is(200)

status.isSuccess      // 2xx
status.isRedirect     // 3xx
status.isClientError  // 4xx
status.isServerError  // 5xx
```

- assert response headers

```scala
headers.name("cache-control").isPresent

headers.contain("cache-control" -> "no-cache")

headers.name("cache-control").isAbsent

headers.hasSize(3)

save_header_value("cache-control" -> "my-cache-control-value")
```

- assert response body with different modes (ignoring, whitelisting)

```scala
body.is(
  """
  {
    "name": "Batman",
    "realName": "Bruce Wayne",
    "city": "Gotham city",
    "hasSuperpowers": false,
    "publisher":{
      "name":"DC",
      "foundationYear":1934,
      "location":"Burbank, California"
    }
  }
  """)

body.ignoring("city", "hasSuperpowers", "publisher.foundationYear", "publisher.location").is(
  """
  {
    "name": "Batman",
    "realName": "Bruce Wayne",
    "publisher":{
      "name":"DC"
    }
  }
  """)

body.whitelisting.is(
  """
  {
    "name": "Batman",
    "realName": "Bruce Wayne",
    "publisher":{
      "name":"DC"
    }
  }
  """)
```

Ignored keys and extractors use [JSON path](../syntax/json-path.md) expressions (e.g. `a.b.c[0].d`, `items[*].name`).

JsonPath can also be used to only assert part of the response

```scala
body.path("city").is("Gotham city")

body.path("hasSuperpowers").is(false)

body.path("publisher.name").is("DC")

body.path("city").containsString("Gotham")

body.path("superheroes[*].name").is("""[ "Spiderman", "IronMan", "Superman", "GreenLantern", "Batman" ]""")

body.path("publisher.foundationYear").is(1934)

body.path("publisher.foundationYear").isPresent

body.path("publisher.foundationMonth").isAbsent

body.path("name").matchesRegex("B.*n".r)

body.path("publisher.foundationYear").isLessThan(2000)

body.path("publisher.foundationYear").isGreaterThan(1900)

body.path("publisher.foundationYear").isBetween(1900, 2000)
```

It is possible to handle null values, given the following response body `{ "data" : null }`

```scala
body.path("data").isAbsent  //incorrect
body.path("data").isPresent //correct
body.path("data").isNull    //correct
body.path("data").isNotNull //incorrect
```

If one key of the path contains a "." it has to be wrapped with "`" to notify the parser.

```scala
body.path("`message.en`").isPresent

body.path("`message.fr`").isAbsent
```

To address a root array use `$` followed by the index to access.

```scala

body.path("$[2].name")
```

If the endpoint returns a collection, the response body assertion has several options (ordered, ignoring, and using data tables)

```scala
body.asArray.inOrder.ignoringEach("city", "hasSuperpowers", "publisher").is(
  """
  [{
    "name": "Batman",
    "realName": "Bruce Wayne"
  },
  {
    "name": "Superman",
    "realName": "Clark Kent"
  }]
  """)

body.asArray.inOrder.ignoringEach("publisher").is(
 """
  |    name     |    realName    |     city      |  hasSuperpowers |
  | "Batman"    | "Bruce Wayne"  | "Gotham city" |      false      |
  | "Superman"  | "Clark Kent"   | "Metropolis"  |      true       |
 """)

body.asArray.hasSize(2)
body.asArray.size.is(2) //equivalent to above
body.asArray.size.isLessThan(3)
body.asArray.size.isGreaterThan(1)
body.asArray.size.isBetween(1, 3)

body.asArray.isEmpty

body.asArray.isNotEmpty

body.asArray.contains(
  """
  {
    "name": "Batman",
    "realName": "Bruce Wayne",
    "city": "Gotham city",
    "hasSuperpowers": false,
    "publisher":{
      "name":"DC",
      "foundationYear":1934,
      "location":"Burbank, California"
    }
  }
  """)

body.asArray.not_contains(
  """
  {
    "name": "Joker"
  }
  """)
```

@:callout(info)
`body` expects JSON content. When receiving non-JSON payloads, use `body_raw` which offers `String` like assertions.
@:@

```scala
body_raw.containsString("xml")
```

## HTTP streams

- Server-Sent Events (SSE)

```scala
When I open_sse(s"http://superhero.io/stream", takeWithin = 1.seconds).withParams("justName" -> "true")

Then assert body.asArray.hasSize(2)

Then assert body.is("""
  |   eventType      |    data     |  id  | retry | comment |
  | "superhero name" |  "Batman"   | null | null  |   null  |
  | "superhero name" | "Superman"  | null | null  |   null  |
""")
```

SSE streams are aggregated over a period of time in an array, therefore the previous array predicates can be re-used.


## GraphQL support

Cornichon integrates with the [Sangria](https://github.com/sangria-graphql/sangria) library to provide convenient features for testing GraphQL APIs.


- GraphQL query

```scala
import sangria.macros._

 When I query_gql("/<project-key>/graphql").withQuery(
    graphql"""
      query MyQuery {
        superheroes {
          results {
            name
            realName
            publisher {
              name
            }
          }
        }
      }
    """
    )
```

`query_gql` can also be used for mutation queries.


- GraphQL JSON

The `gqljson` string interpolator lets you write JSON without quoting keys — using GraphQL's input syntax. This can make assertions more readable when dealing with large JSON objects.

```scala
import com.github.agourlay.cornichon.json.CornichonJson._

And assert body.ignoring("city", "publisher").is(
  gqljson"""
  {
    name: "Batman",
    realName: "Bruce Wayne",
    hasSuperpowers: false
  }
  """)
```

The `gqljson` format supports:

- Unquoted keys: `name` instead of `"name"`
- String values still use quotes: `"Batman"`
- Numbers, booleans, and null are unquoted: `42`, `true`, `null`
- Nested objects and arrays: `{ publisher: { name: "DC" } }`
- [Placeholders](../syntax/placeholders.md) work inside `gqljson` strings: `name: "<hero-name>"`

It can be used anywhere a regular JSON string is accepted — body assertions, `is`, `contains`, etc. It is powered by the [Sangria](https://github.com/sangria-graphql/sangria) GraphQL parser.

## Custom HTTP body type

By default, the HTTP DSL expects a `String` body. To use a custom type, provide three typeclass instances:

- `cats.Show` — used to print the values
- `io.circe.Encoder` — used to convert the values to JSON
- `com.github.agourlay.cornichon.resolver.Resolvable` — used to provide a String form in which [placeholders](../syntax/placeholders.md) can be resolved

For instance, to use `JsObject` from `play-json` as an HTTP request body:

```scala
  lazy implicit val jsonResolvableForm: Resolvable[JsObject] = new Resolvable[JsObject] {
    override def toResolvableForm(s: JsObject) = s.toString()
    override def fromResolvableForm(s: String) = Json.parse(s).as[JsObject]
  }

  lazy implicit val showJson: Show[JsObject] = new Show[JsObject] {
    override def show(f: JsObject): String = f.toString()
  }

  lazy implicit val JsonEncoder: Encoder[JsObject] = new Encoder[JsObject] {
    override def apply(a: JsObject): Json = parse(a.toString()).getOrElse(cJson.Null)
  }
```
