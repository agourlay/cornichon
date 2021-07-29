---
layout: docs
title:  "Generators"
---

# Generators

At the center of property based testing lies the capacity to generate arbitrary values that will be used to verify if a given invariant holds.

A `generator` is simply a function that accepts a `RandomContext` which is propagated throughout the execution, for instance below is an example generating Strings and Integers.

There are tree concrete instances of `generators`:
- `ValueGenerator`
- `SessionValueGenerator` which provides additionally the `Session`
- `OptionalValueGenerator` to fail in a controlled fashion

```scala
def stringGen(rc: RandomContext): ValueGenerator[String] = ValueGenerator(
  name = "an alphanumeric String (20)",
  gen = () ⇒ rc.alphanumeric(20))

def integerGen(rc: RandomContext): ValueGenerator[Int] = ValueGenerator(
  name = "integer",
  gen = () ⇒ rc.nextInt(10000))
```

This approach also supports embedding `Scalacheck's Gen` into a `Generator` by propagating the initial seed.

```scala
import org.scalacheck.Gen
import org.scalacheck.rng.Seed

sealed trait Coin
case object Head extends Coin
case object Tail extends Coin

def coinGen(rc: RandomContext): Generator[Coin] = OptionalValueGenerator(
  name = "a Coin",
  gen = () ⇒ {
    val nextSeed = rc.nextLong()
    val params = Gen.Parameters.default.withInitialSeed(nextSeed)
    val coin = Gen.oneOf[Coin](Head, Tail)
    coin(params, Seed(nextSeed))
  }
)
```