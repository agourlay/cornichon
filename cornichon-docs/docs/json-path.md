{%
laika.title = JSON path
%}

# JSON path

JSON paths are used throughout cornichon to navigate into JSON structures. They appear in body assertions (`body.path(...)`), save steps (`save_body_path`), [custom extractors](placeholders.md#custom-extractors), and [ignoring keys](dsl/http-steps.md#http-assertions).

## Syntax

A JSON path is a dot-separated sequence of segments. Each segment selects a field, an array element, or projects over an array.

### Field access

Select a field by name:

```
name                → selects the "name" field
publisher.name      → selects "name" inside "publisher"
```

```scala
body.path("publisher.name").is("DC")
```

### Array element access

Select an array element by index (zero-based):

```
items[0]            → first element of "items" array
items[2].name       → "name" field of the third element
```

```scala
body.path("episodes[0].title").is("Winter Is Coming")
```

### Root array access

When the response itself is a JSON array (not an object), use `$` to refer to the root:

```
$[0]                → first element of the root array
$[0].name           → "name" field of the first element
```

```scala
body.path("$[0].name").is("Batman")
```

### Wildcard projection

Use `[*]` to project over all elements of an array. The result is a JSON array containing the selected values from each element:

```
items[*].name       → array of all "name" values from "items"
$[*].id             → array of all "id" values from a root array
```

```scala
// Given response: [{"name": "Batman"}, {"name": "Superman"}]
body.path("$[*].name").is("""["Batman", "Superman"]""")
```

### Keys containing dots

If a field name contains a dot, wrap it in backticks to prevent it from being interpreted as a path separator:

```
`field.name`        → selects the field literally named "field.name"
`my.key`.nested     → "nested" inside the field "my.key"
```

```scala
body.path("`message.en`").is("Hello")
```

## Where JSON paths are used

| Context | Example |
|---|---|
| Body assertions | `body.path("publisher.name").is("DC")` |
| Save from body | `save_body_path("id" -> "product-id")` |
| Save nested value | `save_body_path("data.items[0].id" -> "first-id")` |
| Ignoring keys | `body.ignoring("publisher.location").is(...)` |
| Array ignoring | `body.asArray.ignoringEach("metadata.internal").is(...)` |
| Custom extractors | `JsonMapper("response", "data.results[0].id")` |
| Session JSON access | `session_value("response").asJson.path("a.b").is(...)` |

## Characters not allowed in field names

Field names cannot contain: `\r`, `\n`, `[`, `]`, `` ` ``, or spaces. Use backtick quoting for fields containing dots.
