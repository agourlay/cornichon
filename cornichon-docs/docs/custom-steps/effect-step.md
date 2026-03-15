{%
laika.title = Effect steps
%}

# EffectStep

An `EffectStep` can be understood as the following function `ScenarioContext => IO[Either[CornichonError, Session]]`.

This means that an `EffectStep` runs a side effect and populates the `Session` with potential result values or returns an error.

A `Session` is a Map-like object used to propagate state throughout a `scenario`. It is used to resolve [placeholders](../placeholders.md#placeholders) and save the result computations for later assertions.

Here is the simplest `EffectStep` possible:

```scala
When I EffectStep(title = "do nothing", action = scenarioContext => IO.pure(Right(scenarioContext.session)))
```

or using a factory helper when dealing with computations that do not fit the `EffectStep` type.

```scala
When I EffectStep.fromSync(title = "do nothing", action = scenarioContext => scenarioContext.session)
When I EffectStep.fromSyncE(title = "do nothing", action = scenarioContext => Right(scenarioContext.session))
```

Let's try to save a value into the `Session`

```scala
When I EffectStep.fromSync(title = "estimate PI", action = scenarioContext => scenarioContext.session.addValueUnsafe("result", piComputation()))
```

The test engine is responsible for controlling the execution of the side effect function and for reporting any error.

The `EffectStep` uses `cats-effect` `IO` under the hood. It is also possible to import the cats `EffectStep` explicitly:

```scala
import com.github.agourlay.cornichon.steps.cats.EffectStep
```


# EffectStep using the HTTP service

Sometimes you want to perform HTTP calls inside an `EffectStep`, this is where the `httpService` comes in handy.

In order to illustrate its usage let's take the following example, you would like to write a custom step like:

```scala
def feature = Feature("Customer endpoint") {

  Scenario("create customer") {

    When I create_customer

    Then assert status.is(201)

  }
}
```

Most of the time you will create your own trait containing your custom steps and declare a self-type on `CornichonFeature` to be able to access the `httpService`.

It exposes a method `requestEffectIO` turning an `HttpRequest` into an asynchronous effect.

```scala
trait MySteps {
  this: CornichonFeature =>

  def create_customer = EffectStep(
    title = "create new customer",
    effect = http.requestEffectIO(
      request = HttpRequest.post("/customer").withBody("someJson"),
      expectedStatus = Some(201),
      extractor = RootExtractor("customer")
    )
  )
}
```

The built-in HTTP steps available on the DSL are actually built on top of the `httpService` which means that you benefit from all the existing infrastructure to:

- resolve placeholders in URL, query params, body and headers.
- automatically populate the session with the results of the call such as response body, status and headers (it is also possible to pass a custom extractor).
- handle common errors such as timeout and malformed requests.
