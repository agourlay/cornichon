{%
laika.title = Wrapper steps
%}

# Wrapper steps

Wrapper steps allow to control the execution of a series of steps to build more powerful tests.

- repeating a series of `steps`

```scala
Repeat(3) {
  When I get("http://superhero.io/batman")

  Then assert status.is(200)
}
```

- repeating a series of `steps` with access to the current iteration index

```scala
Repeat(3, "index") {
  When I get("http://superhero.io/heroes/<index>")

  Then assert status.is(200)
}
```

- repeating a series of `steps` during a period of time

```scala
RepeatDuring(300.millis) {
  When I get("http://superhero.io/batman")

  Then assert status.is(200)
}
```

- repeat a series of `steps` for each input element

```scala
RepeatWith("Superman", "GreenLantern", "Spiderman")("superhero-name") {

  When I get("/superheroes/<superhero-name>").withParams("sessionId" -> "<session-id>")

  Then assert status.is(200)

  Then assert body.path("hasSuperpowers").is(true)
}
```

- retry a series of `steps` until it succeeds or reaches the limit

```scala
RetryMax(3) {
  When I get("http://superhero.io/batman")

  Then assert status.is(200)
}
```


- repeating a series of `steps` until it succeeds over a period of time at a specified interval (handy for eventually consistent endpoints)

```scala
Eventually(maxDuration = 15.seconds, interval = 200.milliseconds) {

    When I get("http://superhero.io/random")

    Then assert body.ignoring("hasSuperpowers", "publisher").is(
      """
      {
        "name": "Batman",
        "realName": "Bruce Wayne",
        "city": "Gotham city"
      }
      """
    )
  }
```

- execute a series of steps 'times' times in batches of `parallelism` in parallel and wait 'maxTime' for completion.

```scala
RepeatConcurrently(times = 10, parallelism = 3, maxTime = 10 seconds) {

  When I get("http://superhero.io/batman")

  Then assert status.is(200)
}
```

- execute each step in parallel and wait 'maxTime' for completion.

```scala
Concurrently(maxTime = 10 seconds) {

  When I get("http://superhero.io/batman")

  When I get("http://superhero.io/superman")
}
```


- execute a series of steps and fails if the execution does not complete within 'maxDuration'.

```scala
Within(maxDuration = 10 seconds) {

  When I get("http://superhero.io/batman")

  Then assert status.is(200)
}
```

- repeat a series of `steps` for each element in an `Iterable` (more flexible version of `RepeatWith`)

```scala
val heroes = List("Superman", "GreenLantern", "Spiderman")

RepeatFrom(heroes)("superhero-name") {

  When I get("/superheroes/<superhero-name>").withParams("sessionId" -> "<session-id>")

  Then assert status.is(200)
}
```

- repeat a series of steps with different inputs specified via a data-table

```scala
WithDataInputs(
  """
    | a | b  | c  |
    | 1 | 3  | 4  |
    | 7 | 4  | 11 |
    | 1 | -1 | 0  |
  """
) {
  Then assert a_plus_b_equals_c
}

def a_plus_b_equals_c =
  AssertStep("sum of 'a' + 'b' = 'c'", sc => GenericEqualityAssertion(sc.session.getUnsafe("a").toInt + sc.session.getUnsafe("b").toInt, sc.session.getUnsafe("c").toInt))
```

- repeat a series of steps with different inputs specified via JSON

```scala
WithJsonDataInputs(
  """
  [
    { "a": "1", "b": "3", "c": "4" },
    { "a": "7", "b": "4", "c": "11" },
    { "a": "1", "b": "-1", "c": "0" }
  ]
  """
) {
  Then assert a_plus_b_equals_c
}
```

- WithHeaders automatically sets headers for several steps useful for an authenticated scenario.

```scala
WithHeaders(("Authorization", "Basic QWxhZGRpbjpvcGVuIHNlc2FtZQ==")){
  When I get("http://superhero.io/secured")
  Then assert status.is(200)
}
```

- WithBasicAuth automatically sets basic auth headers for several steps.

```scala
WithBasicAuth("admin", "root"){
  When I get("http://superhero.io/secured")
  Then assert status.is(200)
}
```

- HttpListenTo creates an HTTP server that will be running during the length of the enclosed steps.

This feature is defined in the module `cornichon-http-mock` and requires to extend the trait `HttpMockDsl`.

@:callout(info)
By default, this server responds with 201 to any POST request and 200 for all the rest.
@:@

Additionally, it provides three administration features:
- fetching recorded received requests
- resetting recorded received requests
- toggling on/off the error mode to return HTTP 500 to incoming requests

The server records all requests received as a JSON array of HTTP request for later assertions.

There are two ways to perform assertions on the server statistics, either by querying the session at the end of the block or by contacting directly the server while it runs.

Refer to those [examples](https://github.com/agourlay/cornichon/blob/master/cornichon-http-mock/src/test/scala/com/github/agourlay/cornichon/http/MockServerExample.scala) for more information.

@:callout(warning)
This feature is experimental and may change in the future.
@:@

- Log duration

By default, all `Step` execution times can be found in the logs, but sometimes one needs to time a series of steps.

This is where `LogDuration` comes in handy, it requires a label that will be printed as well to identify results.

```scala
LogDuration(label = "my experiment") {

  When I get("http://superhero.io/batman")

  Then assert status.is(200)
}
```
