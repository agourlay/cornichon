{%
laika.title = HTTP steps
%}

# HTTP steps

Cornichon has a set of built-in steps for various HTTP calls and assertions on the response.


## HTTP effects

- GET, DELETE, HEAD, OPTIONS, POST, PUT and PATCH use the same request builder for request's body, URL parameters and headers.

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

There is a built-in support for HTTP body defined as String, if you wish to use other types please check out the section [Custom HTTP body type](#custom-http-body-type).

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

headers.name("cache_control").isAbsent

headers.hasSize(3)

save_header_value("cache_control" -> "my-cache-control-value")
```

- assert response body comes with different flavors (ignoring, whitelisting)

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

Ignored keys and extractors are JsonPaths following the format `a.b.c[index].d`.

The `index` value is either:

- an Integer addressing the array position.
- a `*` to target all values, the result will be an array of the projected values.

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

If the endpoint returns a collection, assert response body has several options (ordered, ignoring and using data table)

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

- Server-Sent-Event.

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

Cornichon offers an integration with the library [Sangria](https://github.com/sangria-graphql/sangria) to propose convenient features to test GraphQL API.


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

`query_gql` can also be used for mutation query.


- GraphQL JSON

all built-in steps accepting String input/output can also accept an alternative lightweight JSON format using the `gqljson` StringContext.

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

## Custom HTTP body type

By default, the HTTP DSL expects a `String` body. To use a custom type, provide three typeclass instances:

- `cats.Show` — used to print the values
- `io.circe.Encoder` — used to convert the values to JSON
- `com.github.agourlay.cornichon.resolver.Resolvable` — used to provide a String form in which [placeholders](../placeholders.md) can be resolved

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
