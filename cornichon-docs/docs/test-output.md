{%
laika.title = Test output
%}

# Understanding test output

When running tests, Cornichon produces structured logs that describe what happened at each step. Understanding this output is key to quickly diagnosing failures.

## Successful scenario

A passing scenario prints a tree of executed steps with their durations:

```
Starting scenario 'Superheroes are accessible'
- Superheroes are accessible (35 ms)

   Scenario : Superheroes are accessible
      main steps
      Given I GET /superheroes/batman (20 ms)
      Then assert status is '200' (0 ms)
      Then assert response body is { ... } (5 ms)
```

Each step is indented under its section (`main steps`, `before steps`, `finally steps`). Durations appear at the end of each line.

## Failed scenario

When a step fails, the output marks the failure clearly:

```
- **failed** Superheroes are accessible (25 ms)

  Scenario 'Superheroes are accessible' failed:

  at step:
  Then assert status is '200'

  with error(s):
  expected status code '200' but '400' was received with body:
  "Not Found"

  seed for the run was '1542985803071'

  replay only this scenario with the command:
  testOnly *SuperHeroesScenario -- "Superheroes are accessible" "--seed=1542985803071"
```

The failure message contains:

- **Which step failed** — the step title after `at step:`
- **What went wrong** — the detailed error after `with error(s):`
- **The seed** — for reproducing the exact same run (useful with randomized tests)
- **The replay command** — a ready-to-use SBT command to re-run just this scenario with the same seed

## Common error messages

### Equality assertion

The most common failure compares expected vs actual values:

```
expected result was:
'Batman'
but actual result is:
'Superman'
```

For JSON bodies, a diff is included to help spot the difference in large payloads.

### Status code mismatch

```
expected status code '200' but '404' was received with body:
"Not Found"
and with headers:
'Content-Type' -> 'text/plain'
```

The response body and headers are included so you can understand why the server returned an unexpected status.

### JSON path not found

```
expected key 'city' was not found in object:
{ "name": "Batman", "realName": "Bruce Wayne" }
```

### Array assertion

```
expected array to contain
'{ "name": "Batman" }'
but it is not the case with array:
[ { "name": "Superman" } ]
```

## Nested step failures

[Wrapper steps](dsl/wrapper-steps.md) like `Eventually`, `Repeat`, and `RetryMax` show their nested execution tree.

### Eventually block

When an `Eventually` block succeeds after retries, the output shows the retries and the final success:

```
Eventually block with maxDuration = 10 seconds and interval = 10 milliseconds
   When I GET /products-search (5 ms)
   Then assert status is '200' (0 ms)
   And assert response body array contains { ... } *** FAILED ***
   expected array to contain '{ ... }' but it is not the case with array: []
   When I GET /products-search (2 ms)
   Then assert status is '200' (0 ms)
   And assert response body array contains { ... } (0 ms)
Eventually block succeeded after '552' retries with '1' distinct errors (8972 ms)
```

When it times out:

```
Eventually block did not complete in time after having being tried '200' times
```

### Repeat block

```
Repeat block failed at occurrence 3
caused by:
expected result was:
'200'
but actual result is:
'500'
```

## Multiple failures

If both the main steps and the `finally` steps fail, all errors are reported together:

```
Scenario 'cleanup test' failed:

at step:
main assertion

with error(s):
expected result was:
'true'
but actual result is:
'false'

and

at step:
finally assertion

with error(s):
expected result was:
'done'
but actual result is:
'pending'

seed for the run was '1'
```

This ensures cleanup failures are not silently swallowed.

## Reproducing failures

Every failure includes a **seed** that controls all sources of randomness ([placeholders](syntax/placeholders.md), [generators](property-based-testing/generators.md), transitions). To replay the exact same execution:

1. Copy the replay command from the failure output
2. Run it in SBT: `testOnly *MyFeature -- "scenario name" "--seed=123456"`

You can also fix the seed permanently in your feature for debugging — see [Feature Options](feature-options.md#seed).

## Console markers

In the console, scenario results are prefixed with status markers:

- `- scenario name (35 ms)` — success
- `- **failed** scenario name (25 ms)` — failure
- `- **ignored** scenario name (reason)` — skipped via `ignoredBecause`
- `- **pending** scenario name` — not yet implemented via `pending`

## Enabling request tracing

If you need more detail about HTTP requests and responses, enable tracing in your `application.conf`:

```hocon
cornichon {
  traceRequests = true
}
```

This logs the full request and response details for every HTTP call. See [Reference Configuration](reference-configuration.md) for all available options.
