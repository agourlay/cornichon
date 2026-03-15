{%
laika.title = PBT
%}

# Property based testing support

Cornichon offers support for Property based testing via two different flavours of testing:

- [ForAll](pbt/for-all.md) — validate that an invariant holds for any generated values
- [Random Model Exploration](pbt/random-model-exploration.md) — explore state machines defined as Markov chains

Both approaches rely on [Generators](pbt/generators.md) to produce random inputs.