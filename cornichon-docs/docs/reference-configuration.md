{%
laika.title = Reference configuration
%}

# Reference configuration

Cornichon can be configured via a `/src/test/resources/application.conf` file using [HOCON](https://github.com/lightbend/config/blob/main/HOCON.md) syntax.

Some of these options can also be overridden programmatically per feature — see [Feature Options](feature-options.md).

All keys live under the `cornichon` namespace. Only the keys you want to change need to be specified — everything else uses the defaults shown below.

```hocon
cornichon {
  requestTimeout = 2000 millis
  globalBaseUrl = ""
  executeScenariosInParallel = true
  scenarioExecutionParallelismFactor = 1
  traceRequests = false
  warnOnDuplicateHeaders = false
  failOnDuplicateHeaders = false
  addAcceptGzipByDefault = false
  disableCertificateVerification = false
  followRedirect = false
  enableHttp2 = false
}
```

## Key reference

### requestTimeout

Default: `2000 millis`

Maximum time to wait for an HTTP response before failing. Accepts any [HOCON duration](https://github.com/lightbend/config/blob/main/HOCON.md#duration-format) value such as `5 seconds` or `500 millis`. Can also be overridden per feature with `override lazy val requestTimeout`.

### globalBaseUrl

Default: `""`

Base URL prepended to all HTTP requests. When set, steps only need the path: `get("/users")` instead of `get("http://localhost:8080/users")`. Can also be overridden per feature with `override lazy val baseUrl`.

### executeScenariosInParallel

Default: `true`

When enabled, scenarios within a feature run concurrently. Disable this if your scenarios share mutable state. See [Execution model](feature-options.md#execution-model) for details.

### scenarioExecutionParallelismFactor

Default: `1`

Controls the number of concurrent scenarios: `factor * number of CPUs + 1`. Increase this for IO-bound test suites where scenarios spend most of their time waiting on HTTP responses. See [Performance tuning](performance-tuning.md#scenario-parallelism) for guidance.

### traceRequests

Default: `false`

When enabled, prints the full details of every HTTP request and response to the console. Useful for debugging but very verbose.

### warnOnDuplicateHeaders

Default: `false`

Logs a warning when the same header name appears more than once in a request. Helps catch accidental header duplication from `withHeaders` / `addHeaders` combinations.

### failOnDuplicateHeaders

Default: `false`

Fails the step when the same header name appears more than once in a request. Stricter than `warnOnDuplicateHeaders`.

### addAcceptGzipByDefault

Default: `false`

Adds `Accept-Encoding: gzip, deflate` to all outgoing requests. The response is automatically decompressed. Disabled by default due to a small performance overhead on the decompression.

### disableCertificateVerification

Default: `false`

Disables TLS certificate verification for HTTPS requests. Useful when testing against servers with self-signed certificates. Do not enable this outside of test environments.

### followRedirect

Default: `false`

Automatically follows HTTP redirects (3xx responses) up to 10 hops. When disabled, redirect responses are returned as-is so you can assert on the status code and `Location` header.

### enableHttp2

Default: `false`

Enables HTTP/2 for outgoing requests. The server must support HTTP/2 for this to have any effect.
