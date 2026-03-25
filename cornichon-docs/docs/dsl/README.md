{%
laika.title = DSL
%}

# DSL

The content of a `feature` is described using a domain-specific language (DSL) providing a clear structure for statement definitions.

The structure of a step statement is the following:

1 - starts with either `Given` - `When` - `And` - `Then`

The prefixes do not change the behavior of the steps but are present to improve the readability.


2 - followed by any single word (could be several words wrapped in back-ticks)

This structure was chosen to increase the freedom of customization while still benefiting from Scala's infix notation.


3 - ending with a `step` definition

The usage pattern is often to first run a `step` with a side effect then assert an expected state in a second `step`.

For example:

```scala
Given I step_definition

When a step_definition

And `another really important` step_definition

Then assert step_definition
```

`step_definition` stands here for any object of type `Step`, those can be manually defined or simply built-in in Cornichon.

## Built-in steps

- [HTTP Steps](http-steps.md) — HTTP effects, assertions, streams, and GraphQL support
- [Session Steps](session-steps.md) — saving, reading, and asserting session values
- [Wrapper Steps](wrapper-steps.md) — repeat, retry, eventually, concurrently, and more
- [HTTP Mock](http-mock.md) — spin up a temporary mock server (requires `cornichon-http-mock` module)
- [Utility Steps](utility-steps.md) — debug helpers and DSL composition
