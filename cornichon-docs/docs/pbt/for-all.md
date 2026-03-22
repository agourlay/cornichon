{%
laika.title = ForAll
%}

# ForAll

`for_all` verifies that an invariant holds for any generated values. It's the simplest form of property based testing in cornichon.

## Minimal example

```scala
def intGen(rc: RandomContext) = ValueGenerator(
  name = "integer",
  gen = () => rc.nextInt(100))

Scenario("positive integers are positive") {
  Given check for_all("positive check", maxNumberOfRuns = 10, intGen) { n =>
    AssertStep("n >= 0", _ => GenericEqualityAssertion(true, n >= 0))
  }
}
```

That's it — define a generator, pass it to `for_all`, and write your assertion using the generated value.

## With HTTP steps

When your invariant involves multiple steps (HTTP calls + assertions), wrap them in `Attach` to combine them into a single step:

```scala
Scenario("reverse a string twice yields the same result") {

  Given check for_all("double reverse", maxNumberOfRuns = 5, stringGen) { randomString =>
    Attach {
      Given I post("/double-reverse").withParams("word" -> randomString)
      Then assert status.is(200)
      Then assert body.is(randomString)
    }
  }
}

def stringGen(rc: RandomContext) = ValueGenerator(
  name = "alphanumeric String (20)",
  gen = () => rc.alphanumeric(20))
```

@:callout(info)
`Attach` is needed because `for_all`'s builder function must return a single `Step`. When you have multiple steps (request + assertions), `Attach` groups them into one.
@:@

## Multiple generators

Use additional generator arguments for multi-value properties. Each value is passed as a separate parameter:

```scala
def intGen(rc: RandomContext) = ValueGenerator("int", () => rc.nextInt(100))

Scenario("addition is commutative") {
  Given check for_all("a + b == b + a", maxNumberOfRuns = 10, intGen, intGen) { a => b =>
    AssertStep("commutative", _ => GenericEqualityAssertion(a + b, b + a))
  }
}
```

Up to 6 generators are supported.

## API

```scala
def for_all[A](description: String, maxNumberOfRuns: Int, ga: RandomContext => Generator[A])
              (builder: A => Step): Step

def for_all[A, B](description: String, maxNumberOfRuns: Int,
                   ga: RandomContext => Generator[A],
                   gb: RandomContext => Generator[B])
                  (builder: A => B => Step): Step

// ... up to 6 type parameters
```

## Log output

Each run shows the generated values and step results:

```
ForAll 'alphanumeric String (20)' check 'double reverse' with maxNumberOfRuns=5 and seed=1542985803071
   Run #0
      Given I POST /double-reverse with query parameters 'word' -> 'vtKxhkCJaVlAOzhdSCwD' (1257 millis)
      Then assert status is '200' (7 millis)
      Then assert response body is vtKxhkCJaVlAOzhdSCwD (32 millis)
   Run #1
      Given I POST /double-reverse with query parameters 'word' -> '1bmmb2urTfJy59J2gGtI' (5 millis)
      ...
```

The seed is printed so you can [reproduce failures](../feature-options.md#seed).

## Next steps

- [Generators](generators.md) — built-in generators and ScalaCheck integration
- [Random Model Exploration](random-model-exploration.md) — for testing stateful API workflows with Markov chains
