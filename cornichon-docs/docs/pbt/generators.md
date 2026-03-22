{%
laika.title = Generators
%}

# Generators

At the center of property based testing lies the capacity to generate arbitrary values that will be used to verify if a given invariant holds.

A `generator` is a function from `RandomContext` to `Generator[A]`. The `RandomContext` provides a seeded random number generator for reproducible test runs.

## Built-in generators

The simplest generators use `ValueGenerator` which wraps a `() => A` thunk:

```scala
def stringGen(rc: RandomContext): ValueGenerator[String] = ValueGenerator(
  name = "an alphanumeric String (20)",
  gen = () => rc.alphanumeric(20))

def integerGen(rc: RandomContext): ValueGenerator[Int] = ValueGenerator(
  name = "integer",
  gen = () => rc.nextInt(10000))
```

## Generator types

There are three concrete generator types:

- **`ValueGenerator[A]`** — wraps a `() => A` thunk. The most common choice.
- **`OptionalValueGenerator[A]`** — wraps a `() => Option[A]`. The test fails with a clear error when `None` is generated. Use this when your generator might not produce a value (e.g. ScalaCheck's `Gen` can return `None`).
- **`SessionValueGenerator[A]`** — wraps a `Session => A`. Use this when the generated value depends on the current test state (e.g. picking an ID that was saved by a previous step).

```scala
// Generate a value that depends on session state
def idGen(rc: RandomContext): SessionValueGenerator[String] = SessionValueGenerator(
  name = "an existing product ID",
  gen = session => session.getUnsafe("product-id"))
```

## Using ScalaCheck generators

ScalaCheck's `Gen[A]` can be wrapped into a cornichon `Generator[A]`. The key is to propagate the seed from `RandomContext` into ScalaCheck's `Gen.Parameters` so that test runs are reproducible:

```scala
import org.scalacheck.Gen
import org.scalacheck.rng.Seed

// Helper to convert any ScalaCheck Gen into a cornichon Generator
def fromScalacheck[A](name: String, gen: Gen[A])(rc: RandomContext): Generator[A] =
  OptionalValueGenerator(
    name = name,
    gen = () => {
      val nextSeed = rc.nextLong()
      gen(Gen.Parameters.default.withInitialSeed(nextSeed), Seed(nextSeed))
    }
  )
```

Usage:

```scala
// Define once
val coinGen = fromScalacheck("a Coin", Gen.oneOf(Head, Tail)) _

// Use in for_all or check_model
check for_all("flip coins", 100, coinGen) { coin =>
  // coin is Head or Tail
  AssertStep("valid coin", _ => ...)
}
```

The helper uses `OptionalValueGenerator` because `Gen.apply` returns `Option[A]` — it can return `None` if the generator's filter conditions aren't met.
