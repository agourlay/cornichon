{%
laika.title = HTTP mock
%}

# HTTP Mock

@:callout(info)
This feature requires the `cornichon-http-mock` module as an additional dependency:
```scala
libraryDependencies += "com.github.agourlay" %% "cornichon-http-mock" % "0.23.0" % Test
```
Your feature class must also extend the `HttpMockDsl` trait.
@:@

`HttpListenTo` spins up a temporary HTTP server for the duration of a block, letting you test how your code interacts with external services.

The API is `HttpListenTo(interface, portRange)(label)`. It is common to define a convenience alias:

```scala
def HttpMock = HttpListenTo(interface = None, portRange = Some(Range(8080, 8085))) _
```

The examples below assume this alias is defined.

## Default behavior

By default, the mock server:

- responds with **201** to any POST request
- responds with **200** to all other requests (GET, PUT, DELETE, etc.)
- records all incoming requests for later assertions
- the server URL is available as the placeholder `<label-url>` (e.g. `<awesome-server-url>`)

## Example

```scala
 Scenario("reply to POST request with 201 and assert on received bodies") {
    HttpMock("awesome-server") {
      When I post("<awesome-server-url>/heroes/batman").withBody(
        """
        {
          "name": "Batman",
          "realName": "Bruce Wayne",
          "hasSuperpowers": false
        }
        """
      )

      When I post("<awesome-server-url>/heroes/superman").withBody(
        """
        {
          "name": "Superman",
          "realName": "Clark Kent",
          "hasSuperpowers": true
        }
        """
      )

      Then assert status.is(201)

      // HTTP Mock exposes what it received
      When I get("<awesome-server-url>/requests-received")

      Then assert body.asArray.ignoringEach("headers").is(
        """
        [
          {
            "body" : {
              "name" : "Batman",
              "realName" : "Bruce Wayne",
              "hasSuperpowers" : false
            },
            "url" : "/heroes/batman",
            "method" : "POST",
            "parameters" : {}
          },
          {
            "body" : {
              "name" : "Superman",
              "realName" : "Clark Kent",
              "hasSuperpowers" : true
            },
            "url" : "/heroes/superman",
            "method" : "POST",
            "parameters" : {}
          }
        ]
      """
      )

    }

    // Once HTTP Mock closed, the recorded requests are dumped in the session
    And assert httpListen("awesome-server").received_calls(2)

    And assert httpListen("awesome-server").received_requests.asArray.ignoringEach("headers").is(
      """
        [
          {
            "body" : {
              "name" : "Batman",
              "realName" : "Bruce Wayne",
              "hasSuperpowers" : false
            },
            "url" : "/heroes/batman",
            "method" : "POST",
            "parameters" : {}
          },
          {
            "body" : {
              "name" : "Superman",
              "realName" : "Clark Kent",
              "hasSuperpowers" : true
            },
            "url" : "/heroes/superman",
            "method" : "POST",
            "parameters" : {}
          }
        ]
      """
    )

    And assert httpListen("awesome-server").received_requests.path("$[0].body.name").is("Batman")

    And assert httpListen("awesome-server").received_requests.path("$[1].body").is(
      """
      {
        "name": "Superman",
        "realName": "Clark Kent",
        "hasSuperpowers": true
      }
      """
    )
 }
```

## Admin endpoints

While the mock server is running, you can control its behavior by sending requests to these admin endpoints:

### GET /requests-received

Returns a JSON array of all recorded requests. Each entry contains `body`, `url`, `method`, `parameters`, and `headers`.

```scala
When I get("<my-server-url>/requests-received")
Then assert body.asArray.hasSize(3)
```

### GET /reset

Clears all recorded requests. Useful when a scenario makes setup requests that you don't want to assert on.

```scala
// Setup phase
When I post("<my-server-url>/setup-data").withBody("...")
// Clear so we only assert on the real test requests
When I get("<my-server-url>/reset")
// Actual test
When I post("<my-server-url>/api/action").withBody("...")
When I get("<my-server-url>/requests-received")
Then assert body.asArray.hasSize(1)
```

### POST /response

Sets a custom response body that the mock server will return for all subsequent non-admin requests.

```scala
When I post("<my-server-url>/response").withBody("""{"status": "ok"}""")
// Now all requests to the mock return {"status": "ok"}
When I get("<my-server-url>/anything")
Then assert body.is("""{"status": "ok"}""")
```

### POST /delayInMs

Adds a delay (in milliseconds) before the mock server responds. Useful for testing timeout handling.

```scala
When I post("<my-server-url>/delayInMs").withBody("500")
// Subsequent requests will take at least 500ms
```

### POST /toggle-error-mode

Toggles error mode on/off. When enabled, the mock server responds with **500 Internal Server Error** to all non-admin requests.

```scala
When I post("<my-server-url>/toggle-error-mode")
// Now all requests return 500
When I get("<my-server-url>/anything")
Then assert status.is(500)
// Toggle back to normal
When I post("<my-server-url>/toggle-error-mode")
```

### POST /toggle-bad-request-mode

Toggles bad request mode on/off. When enabled, the mock server responds with **400 Bad Request** to all non-admin requests.

```scala
When I post("<my-server-url>/toggle-bad-request-mode")
When I get("<my-server-url>/anything")
Then assert status.is(400)
```

## Assertions after the block

Once the `HttpMock` block ends, the server shuts down and its recorded requests are saved into the session. You can assert on them using `httpListen`:

```scala
And assert httpListen("my-server").received_calls(2)
And assert httpListen("my-server").received_requests.asArray.hasSize(2)
And assert httpListen("my-server").received_requests.path("$[0].method").is("POST")
```