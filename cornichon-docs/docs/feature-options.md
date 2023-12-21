---
layout: docs
title:  "Feature options"
position: 7
---

# Feature options

To implement a `CornichonFeature` it is only required to implement the `feature` function. However, a number of useful options are available using override.

## Before and after hooks

Hooks are available to set up and tear down things as usual but this feature is not integrated into the DSL.

Four functions are available in `CornichonFeature` with self-explanatory names:

Taking a `Unit` expression

```scala
beforeFeature {
  // do side effect here
}

afterFeature {
  // do side effect here
}
```

Taking a `Step` expression similar to the main DSL. You can either pass a single regular `Step` or a `WrapperStep` like `Attach`.

Here is an examples with fictitious steps.

```scala
beforeEachScenario {
  Attach {
    Given I setup_server
    Then assert setup_successful
  }
}

afterEachScenario{
  Then I cleanup_resources
}
```

## Base URL

Instead of repeating at each HTTP statement the full URL, it is possible to set a common URL for the entire ```feature``` by overriding:

```scala
override lazy val baseUrl = s"http://localhost:8080"

```

and then only provide the missing part in the HTTP step definition

```scala
 When I get("/superheroes/Batman")

 When I delete("/superheroes/GreenLantern")
```

You can still override the base URL of a single step by providing the complete URL starting with the HTTP protocol.

## Request timeout

The default value for the HTTP request timeout is `2 seconds`. As always it can be overridden per scenario.

```scala
import scala.concurrent.duration._

override lazy val requestTimeout = 100.millis
```

## Seed

On a failure the initial seed will be provided in the error reporting enabling you to replay the exact same test even if it contains source of randomness such as:

  - randomized placeholders (random-uuid, random-string, random-boolean etc.)
  - property based testing generators & transitions
  - custom steps using `ScenarioContext.randomContext`
  - `RandomMapper` as extractor

```scala
override lazy val seed: Option[Long] = Some(1L)
```

## Register custom extractors

In some cases it makes sense to declare `extractors` to avoid code duplication when dealing with `session` values.

An extractor is responsible to describe using a JsonPath how to build a value from an existing value in `session`.

For instance if most of your JSON responses contain a field `id` and you want to use it as a placeholder without always having to manually extract and save the value into the ```session``` you can write :

```scala
   override def registerExtractors = Map(
     "response-id" -> JsonMapper(HttpService.LastResponseBodyKey, "id")
   )
```

It is now possible to use `<response-id>` or `<response-id[integer]>` in the steps definitions.

It works for all keys in `Session`, let's say we also have objects registered under keys `customer` & `product`:


```scala
   override def registerExtractors = Map(
     "response-version" -> JsonMapper(HttpService.LastResponseBodyKey, "version"),
     "customer-street" -> JsonMapper("customer", "address.street"),
     "product-first-rating" -> JsonMapper("product", "rating[0].score")
   )
```


## Execution model

By default, the `features` are executed sequentially and the `scenarios` within are executed in parallel.

This execution is configurable if you have specific constraints.

To run `scenarios` sequentially it is necessary to declare in your application.conf file

```scala
cornichon {
  executeScenariosInParallel = false
}
```

The actual number of concurrent scenario is controlled via the configuration field `scenarioExecutionParallelismFactor` which defaults to 1.

```
number of concurrent scenarios = `scenarioExecutionParallelismFactor` * number of CPU + 1
```

This means using more powerful machines will automatically trigger more scenarios.

To run `features` in parallel it is necessary to manually set a flag in your SBT build file.

```scala
parallelExecution in Test := true
```

or through the command line `sbt test parallelExecution in Test := true`

## Ignoring features or scenarios

`Feature` or individual `scenario` can also be marked to be ignored.

```scala mdoc:silent
import com.github.agourlay.cornichon.CornichonFeature

class CornichonExamplesSpec extends CornichonFeature {

  // Ignore a complete feature
  def feature = Feature("Checking google").ignoredBecause("Your reasons ..."){

    // Ignore a single scenario
    Scenario("Google is up and running").ignoredBecause("Your reasons ..."){

      When I get("http://google.com")

      Then assert status.is(302)
    }
  }
}
```

## Pending scenario

During development, you may want to remember that a `scenario` needs to be created, but you’re not ready to write it yet.

```scala mdoc:silent
import com.github.agourlay.cornichon.CornichonFeature

class CornichonPendingExamplesSpec extends CornichonFeature {

  def feature = Feature("Some title"){

    Scenario("this important use case").pending

    Scenario("that important edge case").pending
  }
}
```


