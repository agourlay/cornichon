---
layout: docs
title:  "Custom steps"
position: 3
---

# Custom steps

## EffectStep

An ```EffectStep``` can be understood as the following function ```Session => Future[Either[CornichonError, Session]]```.

This means that an ```EffectStep``` runs a side effect and populates the ```Session``` with potential result values or returns an error.

A ```session``` is a Map-like object used to propagate state throughout a ```scenario```. It is used to resolve [placeholders](#placeholders) and save the result computations for later assertions.

Here is the most simple ```EffectStep```:

```scala
When I EffectStep(title = "do nothing", action = s => Future.successful(Right(s)))
```

or using a factory helper when dealing with computations that do not fit the ```EffectStep``` type.

```scala
When I EffectStep.fromSync(title = "do nothing", action = s => s)
When I EffectStep.fromSyncE(title = "do nothing", action = s => Right(s))
When I EffectStep.fromAsync(title = "do nothing", action = s => Future(s))
```

Let's try so save a value into the ```Session```

```scala
When I EffectStep.fromSync(title = "estimate PI", action = s => s.add("result", piComputation())
```

The test engine is responsible for controling the execution of the side effect function and to report any error.


## EffectStep using the HTTP service

Sometimes you want to perform HTTP calls inside of of an ```EffectStep```, this is where the ```http``` service comes in handy.

In order to illustrate its usage let's take the following example, you would like to write a custom step like:

```scala
def feature = Feature("Customer endpoint"){

  Scenario("create customer"){

    When I create_customer

    Then assert status.is(201)

  }
```

Most of the time you will create your own trait containing your custom steps and declare a self-type on ```CornichonFeature``` to be able to access the ```httpService```.

It exposes a method ```requestEffect``` turning an ```HttpRequest``` into an asynchronous effect.

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

The built-in HTTP steps available on the DSL are actually built on top of the ```httpService``` which means that you benefit from all the existing infrastructure to:

- resolve placeholders in URL, query params, body and headers.
- automatically populate the session with the results of the call such as response body, status and headers (it is also possible to pass a custom extractor).
- handle common errors such as timeout and malformed requests.

## AssertStep

An ```AssertStep``` can be understood as the following function ```Sesssion => Assertion```. Its goal is to describe an expectation.

The test engine is responsible to test the validity of the provided ```Assertion``` which can be one of the following:

* Equality assertions : test the equality of two objects using the cats ```Equals``` typeclass.
  * GenericEqualityAssertion to leave all the details to Cornichon

    ```scala
    When I AssertStep("always true!", s => GenericEqualityAssertion(true, true))
    ```

  * CustomMessageEqualityAssertion to provide a custom error message

    ```scala
    CustomMessageAssertion[A](expected: A, result: A, customMessage: () ⇒ String)
    ```

* Ordering assertions : compare two objects using the cats ```Order``` typeclass.
  * GreaterThanAssertion
  * LessThanAssertion
  * BetweenAssertion

* Collection assertions : test the state of a collection of elements
  * CollectionEmptyAssertion
  * CollectionNotEmptyAssertion
  * CollectionSizeAssertion
  * CollectionContainsAssertion

* String assertion : assert the content of a given String value
  * StringContainsAssertion
  * RegexAssertion


Below is a longer example showing how to integration an assertion into scenario.

```scala
When I EffectStep.fromSync(
  title = "estimate PI",
  action = s => s.add("result", piComputation())
)

Then assert AssertStep(
  title = "check estimate",
  action = s => Assertion.either{
    s.get("result").map(r => BetweenAssertion(3.1, r.toDouble, 3.2))
  }
)
```

```Assertions``` can also be composed using ```and``` and ```or```, for instance ```BetweenAssertion``` is the result of ```LessThanAssertion``` and ```GreaterThanAssertion```.

This is rather low level therefore you not should write your steps like that directly inside the DSL but hide them behind functions with appropriate names.

Fortunately a bunch of built-in steps and primitive building blocs are already available for you.

Note for advance users: it is also possible to write custom wrapper steps by implementing ```WrapperStep```.

## ResourceStep

A `ResourceStep` is a way to acquire a resource / create some state and make sure that it gets 
released / cleaned up before the end of the `Scenario` even if normal control flow is interrupted
(by an error or a failed assertion for example).

It can be implemented by a pair of `Step`s. One to acquire the resource and another to release it.
Information can be communicated between the two steps the same way we would with any other `Step`,
through the `Session`.

So, for example, in a scenario containing:

```scala
Given I setup_some_fixture_data()
```

where
```scala
def setup_some_fixture_data() = ResourceStep(
  title = "Set up fixture data"
  acquire = EffectStep("insert data", { session =>
    val randomId = insertData()
    session.addValue("id", randomId)
  }),
  release = EffectStep("clean up data", { session =>
    val randomId = session.get("id").right.get
    deleteData(randomId)
  })
)
```

we can be sure that the `clean up data` step runs regardless of what happens after `insert data`.

Multiple `ResourceStep`s are allowed in a `Scenario`. In this case, the `release` `Step` 
of the last `ResourceStep` is run first and we proceed up the `Scenario`. 