{%
laika.title = Performance tuning
%}

# Performance tuning

If your test suite is slow, here is a checklist of options to speed things up.

## Scenario parallelism

By default, scenarios within a feature run in parallel. The number of concurrent scenarios is:

```
scenarioExecutionParallelismFactor * number of CPUs + 1
```

The default factor is `1`. If your scenarios are IO-bound (waiting on HTTP responses, databases, message brokers), increasing this factor lets more scenarios run while others wait:

```hocon
cornichon {
  scenarioExecutionParallelismFactor = 2
}
```

See [Execution model](feature-options.md#execution-model) for details.

## Feature parallelism

By default, features run sequentially. If your features are independent, enable parallel feature execution in your SBT build:

```scala
Test / parallelExecution := true
```

When using the [CLI runner](misc.md), use the `--featureParallelism` flag:

```
--featureParallelism 4
```

## Step-level concurrency

Steps within a scenario run sequentially because each step can depend on the session state from the previous one. When you have independent steps that don't depend on each other, use `Concurrently` to run them in parallel:

```scala
Concurrently(maxTime = 10.seconds) {
  When I get("/api/users")
  When I get("/api/products")
  When I get("/api/orders")
}
```

For load-testing a single operation, use `RepeatConcurrently`:

```scala
RepeatConcurrently(times = 50, parallelism = 10, maxTime = 30.seconds) {
  When I get("/api/health")
  Then assert status.is(200)
}
```

Both are documented in the [DSL wrapper steps](dsl.md#wrapper-steps).

## Request timeout

The default HTTP request timeout is `2 seconds`. If your test target is slow, individual requests may time out and trigger retries or failures. Adjust it per feature:

```scala
override lazy val requestTimeout = 5.seconds
```

Or globally in `application.conf`:

```hocon
cornichon {
  requestTimeout = 5 seconds
}
```

Conversely, if your target is fast, lowering the timeout lets failures surface quicker.

## Reducing test scope

When debugging or iterating on a specific scenario, avoid running the entire suite:

- **Focus on one scenario** with `.focused` to skip all others in the feature — see [Focusing on a scenario](feature-options.md#focusing-on-a-scenario)
- **Use SBT test filters** to run a single feature: `testOnly *MyFeatureSpec`
