{%
laika.title = Session steps
%}

# Session steps

- setting a value in `session`

```scala
save("favorite-superhero" -> "Batman")
```

- saving the entire response body to `session`

```scala
save_body("response-snapshot")
```

- saving a value extracted from the response body to `session`

```scala
save_body_path("city" -> "batman-city")
```

- asserting value in `session`

```scala
session_value("favorite-superhero").is("Batman")
```

- asserting JSON value in `session`

```scala
session_value("my-json-response").asJson.path("a.b.c").ignoring("d").is("...")
```


- asserting existence of value in `session`

```scala
session_value("favorite-superhero").isPresent
session_value("favorite-superhero").isAbsent
```

- transforming a value in `session`

```scala
transform_session("my-key")(_.toUpperCase)
```

- removing a key from `session`

```scala
remove("temporary-key")
```

- rolling back a value in `session` to its previous value

```scala
rollback("favorite-superhero")
```

- comparing two session values

```scala
session_values("key1", "key2").areEquals

session_values("key1", "key2").areNotEquals

session_values("key1", "key2").asJson.areEquals
```

- comparing current value with previous value of the same key

```scala
session_value("counter").hasEqualCurrentAndPreviousValues

session_value("counter").hasDifferentCurrentAndPreviousValues
```

- asserting on the full history of values for a key

```scala
session_history("status").containsExactly("pending", "active", "completed")
```

- additional assertions on session values

```scala
session_value("name").isNot("Joker")

session_value("name").containsString("Bat")

session_value("name").matchesRegex("B.*n".r)
```
