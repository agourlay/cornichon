{%
laika.title = Property Based Testing
%}

# Property based testing

Instead of testing specific inputs, property based testing generates random inputs and verifies that invariants hold. This catches edge cases that hand-written tests miss.

Cornichon supports two approaches:

- [ForAll](for-all.md) — verify that an invariant holds for any generated values. Use this for stateless properties like "reversing a string twice yields the original".
- [Random Model Exploration](random-model-exploration.md) — explore stateful API workflows by defining them as Markov chains. Use this for testing CRUD APIs, state machines, or eventually-consistent systems.

Both approaches rely on [Generators](generators.md) to produce random inputs. All runs are seeded for reproducibility — a failing test always prints the seed needed to replay the exact same execution.
