{%
laika.title = Data tables
%}

# Data tables

Data tables provide a compact, readable way to express tabular data inline. They can be used in two contexts: as an alternative to JSON arrays in body assertions, and as inputs for data-driven test scenarios.

## Syntax

A data table is a pipe-delimited string. The first row defines the column headers, and subsequent rows define the data:

```
| name       | city         | score |
| "Batman"   | "Gotham"     | 42    |
| "Superman" | "Metropolis" | 99    |
```

**Parsing rules:**

- Columns are separated by `|`
- Leading and trailing whitespace in cells is stripped
- Empty cells are ignored
- String values must be quoted with `"`
- Numbers and booleans are unquoted
- All rows must have the same number of columns as the header

**Escape sequences** are supported in header values using backslash:

| Escape | Character |
|---|---|
| `\\` | backslash |
| `\|` | pipe |
| `\n` | newline |
| `\r` | carriage return |
| `\t` | tab |
| `\b` | backspace |
| `\f` | form feed |
| `\uXXXX` | unicode character |

## Body assertions

Data tables can be used as an alternative to JSON arrays when asserting on collection responses. The table is automatically converted to a JSON array of objects:

```scala
body.asArray.inOrder.is("""
  | name       | realName       | city         |
  | "Batman"   | "Bruce Wayne"  | "Gotham"     |
  | "Superman" | "Clark Kent"   | "Metropolis" |
""")
```

This is equivalent to:

```scala
body.asArray.inOrder.is("""
[
  { "name": "Batman",   "realName": "Bruce Wayne", "city": "Gotham" },
  { "name": "Superman", "realName": "Clark Kent",  "city": "Metropolis" }
]
""")
```

The table format is often more readable when comparing many objects with the same structure. It works with all array assertions including `ignoringEach`:

```scala
body.asArray.inOrder.ignoringEach("publisher").is("""
  | name       | realName       | city         | hasSuperpowers |
  | "Batman"   | "Bruce Wayne"  | "Gotham"     | false          |
  | "Superman" | "Clark Kent"   | "Metropolis" | true           |
""")
```

## Data-driven tests

Use [`WithDataInputs`](../dsl/wrapper-steps.md) to run the same steps for each row of a data table. The column headers become session keys available as [placeholders](placeholders.md):

```scala
WithDataInputs("""
  | endpoint          | expected_status |
  | "/health"         | "200"           |
  | "/api/version"    | "200"           |
  | "/does-not-exist" | "404"           |
""") {
  When I get("<endpoint>")
  Then assert status.is("<expected_status>")
}
```

For more complex inputs, [`WithJsonDataInputs`](../dsl/wrapper-steps.md) accepts a JSON array instead of a table:

```scala
WithJsonDataInputs("""
[
  { "a": "1", "b": "3", "c": "4" },
  { "a": "7", "b": "4", "c": "11" }
]
""") {
  Then assert a_plus_b_equals_c
}
```
