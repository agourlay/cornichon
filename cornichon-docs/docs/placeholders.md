---
layout: docs
title:  "Placeholders"
position: 5
---

# Placeholders

Most built-in steps can use placeholders in their arguments, those will be automatically resolved from the ```session```:

- URL
- Expected body
- HTTP params (name and value)
- HTTP headers (name and value)
- JSON Path

```scala mdoc:silent
import com.github.agourlay.cornichon.CornichonFeature

class PlaceholderFeature extends CornichonFeature {

  def feature =
  Feature("Placeholders examples") {

    Scenario("abstract favorite superheroes") {

      Given I save("favorite-superhero" -> "Batman")

      Then assert session_value("favorite-superhero").is("Batman")

      When I get("http://localhost:8080/superheroes/<favorite-superhero>")

      Then assert body.is(
        """
        {
          "name": "<favorite-superhero>",
          "realName": "Bruce Wayne",
          "city": "Gotham city",
          "publisher": "DC"
        }
        """
      )

      And I save_body_path("city" -> "batman-city")

      Then assert session_value("batman-city").is("Gotham city")

      Then assert body.is(
        """
        {
          "name": "<favorite-superhero>",
          "realName": "Bruce Wayne",
          "city": "<batman-city>",
          "publisher": "DC"
        }
        """
      )
    }
  }
}
```

It is also possible to inject random values inside placeholders using:

- `<random-uuid>` for a random UUID
- `<random-positive-integer>` for a random Integer between 0-10000
- `<random-string>` for a random String of length 5
- `<random-alphanum-string>` for a random alphanumeric String of length 5
- `<random-boolean>` for a random Boolean string
- `<random-timestamp>` for a random timestamp
- `<current-timestamp>` for the current timestamp
- `<scenario-unique-number>` for a unique number scoped per scenario
- `<global-unique-number>` for a globally unique number across all features

```scala
post("http://url.io/somethingWithAnId").withBody(
"""
  {
    "id" : "<random-uuid>"
  }
""")
```

If you save several times a value under the same key, the ```session``` will behave like a Multimap by appending the values.

It becomes then possible to retrieve past values :

- ```<name>``` always uses the latest value taken by the key.
- ```<name[0]>``` uses the first value taken by the key
- ```<name[1]>``` uses the second element taken by the key
