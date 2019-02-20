---
layout: docs
title:  "Effect steps"
---

# EffectStep

An `EffectStep` can be understood as the following function `Session => Future[Either[CornichonError, Session]]`.

This means that an `EffectStep` runs a side effect and populates the `Session` with potential result values or returns an error.

A `Session` is a Map-like object used to propagate state throughout a `scenario`. It is used to resolve [placeholders](#placeholders) and save the result computations for later assertions.

Here is the most simple `EffectStep`:

```scala
When I EffectStep(title = "do nothing", action = s => Future.successful(Right(s)))
```

or using a factory helper when dealing with computations that do not fit the `EffectStep` type.

```scala
When I EffectStep.fromSync(title = "do nothing", action = s => s)
When I EffectStep.fromSyncE(title = "do nothing", action = s => Right(s))
When I EffectStep.fromAsync(title = "do nothing", action = s => Future(s))
```

Let's try so save a value into the `Session`

```scala
When I EffectStep.fromSync(title = "estimate PI", action = s => s.add("result", piComputation())
```

The test engine is responsible for controlling the execution of the side effect function and to report any error.

If you prefer not using the `scala.concurrent.Future` as effect, it is possible to use the `Effect` type from `cats-effect`.

```scala

import com.github.agourlay.cornichon.steps.cats.EffectStep

val myTaskEffect = EffectStep("identity task", s => Task.now(Right(s)))
```


# EffectStep using the HTTP service

Sometimes you want to perform HTTP calls inside of of an `EffectStep`, this is where the `httpService` comes in handy.

In order to illustrate its usage let's take the following example, you would like to write a custom step like:

```scala
def feature = Feature("Customer endpoint"){

  Scenario("create customer"){

    When I create_customer

    Then assert status.is(201)

  }
```

Most of the time you will create your own trait containing your custom steps and declare a self-type on `CornichonFeature` to be able to access the `httpService`.

It exposes a method `requestEffect` turning an `HttpRequest` into an asynchronous effect.

```scala
trait MySteps {
  this: CornichonFeature ⇒

  def create_customer = EffectStep(
    title = "create new customer",
    effect = http.requestEffect(
      request = HttpRequest.post("/customer").withPayload("someJson"),
      expectedStatus = Some(201)
      extractor = RootExtractor("customer")
    )
  )
}
```

The built-in HTTP steps available on the DSL are actually built on top of the `httpService` which means that you benefit from all the existing infrastructure to:

- resolve placeholders in URL, query params, body and headers.
- automatically populate the session with the results of the call such as response body, status and headers (it is also possible to pass a custom extractor).
- handle common errors such as timeout and malformed requests.
