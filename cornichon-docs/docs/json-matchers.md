---
layout: docs
title:  "JSON matchers"
position: 6
---

# JSON matchers

If the exact value of a field is unknown, you can use JSON matchers to make sure it has a certain property or shape.

JSON matchers work more or less like placeholders in practice.

```scala
And assert body.ignoring("city", "realName", "publisher.location").is(
  """
  {
    "name": "<favorite-superhero>",
    "hasSuperpowers": *any-boolean*,
    "publisher": {
      "name": *any-string*,
      "foundationYear": *any-positive-integer*
    }
  }
  """
)
```

You just need to replace the value of the field by one of the built-in JSON matchers *without* quotes.

Here are the available matchers:

- `*is-present*` : checks if the field is defined
- `*is-null*` : checks if the field is null
- `*any-string*` : checks if the field is a String
- `*any-array*` : checks if the field is an Array
- `*any-object*` : checks if the field is an Object
- `*any-integer*` : checks if the field is an Integer
- `*any-positive-integer*` : checks if the field is a positive Integer
- `*any-negative-integer*` : checks if the field is a negative Integer
- `*any-uuid*` : checks if the field is a valid UUID
- `*any-boolean*` : checks if the field is a boolean
- `*any-alphanum-string*` : checks if the field is an alpha-numeric String
- `*any-date*` : checks if the field is a 'yyyy-MM-dd' date
- `*any-date-time*` : checks if the field is a 'yyyy-MM-dd'T'HH:mm:ss.SSS'Z'' datetime
- `*any-time*` : checks if the field is a 'HH:mm:ss.SSS' time

This feature is still fresh and under experimentation therefore it comes with a couple of limitations:
- it is not yet possible to register custom JSON matchers
- matchers are not supported for JSON arrays assertions via `asArray`
