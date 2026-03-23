{%
laika.title = Troubleshooting
%}

# Troubleshooting

Common issues encountered when writing Cornichon tests and how to fix them.

## Placeholder not resolved

**Symptom:** A step receives a literal `<my-key>` string instead of the expected value.

**Cause:** The key does not exist in the [session](dsl/session-steps.md) at the time the placeholder is resolved.

**Fix:** Make sure a previous step saves a value under that key. Common sources:

- A `save_body_path` step is missing or uses a different key name
- The placeholder is used before the step that populates it
- A typo in the key name — placeholders are case-sensitive

```scala
// Wrong: key was never saved
When I get("/users/<user-id>")

// Right: save the key first
And I save_body_path("id" -> "user-id")
When I get("/users/<user-id>")
```

## Using `body` on a non-JSON response

**Symptom:** An error like `expected JSON but got ...` or a parsing failure.

**Cause:** The `body` assertion expects JSON content. If the endpoint returns plain text, XML, or HTML, parsing fails.

**Fix:** Use `body_raw` for non-JSON responses:

```scala
// Wrong: endpoint returns plain text
Then assert body.is("OK")

// Right
Then assert body_raw.is("OK")
Then assert body_raw.containsString("success")
```

## `Eventually` block times out

**Symptom:** `Eventually block did not complete in time after having being tried 'N' times`

**Causes and fixes:**

- **Timeout too short:** Increase `maxDuration`. Eventually-consistent systems can have variable delays.
- **Interval too long:** Reduce `interval` to check more frequently.
- **Assertion never holds:** The system under test may not reach the expected state. Check application logs.
- **Wrong assertion:** Verify your assertion would pass if run manually after the system settles.

```scala
// Too aggressive — might miss the window
Eventually(maxDuration = 1.second, interval = 500.millis) { ... }

// More realistic
Eventually(maxDuration = 15.seconds, interval = 200.millis) { ... }
```

## Scenarios interfere when run in parallel

**Symptom:** Tests pass individually but fail when run together. Flaky failures related to shared data.

**Cause:** By default, scenarios within a feature [run in parallel](feature-options.md#execution-model). If scenarios share mutable state (database records, server state), they can interfere.

**Fixes:**

1. **Make scenarios independent:** Use unique identifiers (e.g., `<random-uuid>`) for test data.
2. **Run sequentially:** Set `executeScenariosInParallel = false` in `application.conf` — see [Reference Configuration](reference-configuration.md).
3. **Use setup/teardown:** Clean up shared state in `beforeEachScenario` / `afterEachScenario`.

```scala
// Use random data to avoid collisions
Given I post("/products").withBody(
  """{ "name": "test-<random-uuid>", "price": 42 }""")
```

## Session key overwritten unexpectedly

**Symptom:** A [placeholder](syntax/placeholders.md) resolves to a different value than expected.

**Cause:** The session behaves like a multimap — saving the same key multiple times appends values, and `<key>` always returns the latest. Inside loops (`Repeat`, `RepeatWith`), each iteration may overwrite the key.

**Fix:** Use indexed access like `<key[0]>` to retrieve earlier values, or use distinct key names. See [Placeholders](syntax/placeholders.md) for the full explanation of session multimap behavior.

## `Repeat` index starts at 1

**Symptom:** Off-by-one errors when using `Repeat` with an index.

**Cause:** The iteration index is one-based.

```scala
Repeat(3, "i") {
  // i takes values: "1", "2", "3"
  When I get("/items/<i>")
}
```

## JSON path returns unexpected value

**Symptom:** A `body.path(...)` assertion fails even though the response looks correct.

**Common causes:**

- **Array vs object:** `body.path("items")` returns the raw JSON array. Use `body.path("items").asArray` for array-specific assertions.
- **Nested path with dots in keys:** If a key contains a `.`, wrap it with backticks: `` body.path("`field.name`") ``
- **Root array access:** For a response that is a JSON array, use `$` as the root: `body.path("$[0].name")`
- **Wildcard projection:** `body.path("items[*].name")` returns an array of all `name` values.

See [JSON path](syntax/json-path.md) for the complete syntax reference.

## Status code assertion fails with response details

**Symptom:**

```
expected status code '200' but '401' was received with body:
"Unauthorized"
```

**Fix:** Cornichon includes the response body and headers in status failures. Read them carefully — they often explain why the server rejected the request (expired token, missing header, wrong content type, etc.).

## Feature not discovered by test framework

**Symptom:** SBT runs but reports 0 tests.

**Cause:** The test framework is not registered, or the feature class is not in `src/test/scala`.

**Fix:** Verify your `build.sbt` includes:

```scala
testFrameworks += new TestFramework("com.github.agourlay.cornichon.framework.CornichonFramework")
```

And that your feature class extends `CornichonFeature` and is in the `src/test/scala` directory.

## Debugging tips

When a test fails and the cause isn't obvious:

1. **Enable request tracing** in `application.conf` to see full HTTP request/response details:

    ```hocon
    cornichon {
      traceRequests = true
    }
    ```

2. **Use debug steps** to inspect state mid-scenario:

    ```scala
    And I show_session
    And I show_last_status
    And I show_last_body_json
    And I show_last_headers
    ```

3. **Replay with a fixed seed** using the command from the failure output to reproduce the exact same execution — see [Understanding Test Output](test-output.md#reproducing-failures).

4. **Focus on one scenario** during debugging to isolate the issue:

    ```scala
    Scenario("the failing one").focused {
      ...
    }
    ```

    See [Feature Options](feature-options.md#focusing-on-a-scenario) for details.
