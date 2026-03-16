{%
laika.title = Assert steps
%}

# AssertStep

An `AssertStep` can be understood as the following function `ScenarioContext => Assertion`. Its goal is to describe an expectation.

The test engine is responsible to test the validity of the provided `Assertion`.

```scala
When I AssertStep("always true!", _ => GenericEqualityAssertion(true, true))
```

## Equality assertions

Test the equality of two objects using the cats `Eq` typeclass.

**GenericEqualityAssertion** compares two values and reports a detailed diff on failure:

```scala
AssertStep("names match", sc => {
  val name = sc.session.getUnsafe("hero-name")
  GenericEqualityAssertion(name, "Batman")
})
```

**CustomMessageEqualityAssertion** lets you provide a custom error message when the default diff is not helpful:

```scala
AssertStep("DB row exists", sc => {
  val count = sc.session.getUnsafe("row-count").toInt
  CustomMessageEqualityAssertion(1, count, () => s"Expected exactly 1 row but found $count")
})
```

## Ordering assertions

Compare two values using the cats `Order` typeclass.

**GreaterThanAssertion** checks that the actual value is strictly greater:

```scala
AssertStep("response time acceptable", sc => {
  val elapsed = sc.session.getUnsafe("elapsed-ms").toDouble
  GreaterThanAssertion(elapsed, 0.0)
})
```

**LessThanAssertion** checks that the actual value is strictly less:

```scala
AssertStep("not too slow", sc => {
  val elapsed = sc.session.getUnsafe("elapsed-ms").toDouble
  LessThanAssertion(elapsed, 5000.0)
})
```

**BetweenAssertion** checks that the actual value falls within a range (exclusive bounds):

```scala
AssertStep("estimate PI", sc => {
  val pi = sc.session.getUnsafe("result").toDouble
  BetweenAssertion(3.1, pi, 3.2)
})
```

## Collection assertions

Test the state of a collection of elements.

```scala
// Check a collection is empty
CollectionEmptyAssertion(List.empty[String])

// Check a collection is not empty
CollectionNotEmptyAssertion(List("Batman", "Superman"))

// Check exact size
CollectionSizeAssertion(List("a", "b", "c"), 3)

// Check that a collection contains a specific element
CollectionContainsAssertion(List("Batman", "Superman"), "Batman")
```

## String assertions

Assert the content of a String value.

**StringContainsAssertion** checks that a string contains a given substring:

```scala
AssertStep("city name contains 'Gotham'", sc => {
  val city = sc.session.getUnsafe("city")
  StringContainsAssertion(city, "Gotham")
})
```

**RegexAssertion** checks that a string matches a regular expression:

```scala
AssertStep("looks like a UUID", sc => {
  val id = sc.session.getUnsafe("id")
  RegexAssertion(id, """[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}""".r)
})
```

## Composing assertions

`Assertions` can be composed using `and` and `or`:

```scala
AssertStep("value in range", sc => {
  val v = sc.session.getUnsafe("value").toDouble
  GreaterThanAssertion(v, 0.0) and LessThanAssertion(v, 100.0)
})
```

## Full example

Below is a complete example showing how to integrate an assertion into a scenario.

```scala
When I EffectStep.fromSync(
  title = "estimate PI",
  action = scenarioContext => scenarioContext.session.addValueUnsafe("result", piComputation())
)

Then assert AssertStep(
  title = "check estimate",
  action = scenarioContext => Assertion.either{
    scenarioContext.session.get("result").map(r => BetweenAssertion(3.1, r.toDouble, 3.2))
  }
)
```

This is rather low level therefore you should not write your steps like that directly inside the DSL but hide them behind functions with appropriate names.

Fortunately a bunch of built-in steps and primitive building blocks are already available for you.

@:callout(info)
For advanced users: it is also possible to write custom wrapper steps by implementing `WrapperStep`. See the [Wrapper Steps](../dsl/wrapper-steps.md) section in the DSL reference.
@:@
