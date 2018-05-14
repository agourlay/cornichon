---
layout: docs
title:  "Placeholders & matchers"
position: 5
---

# Placeholders

Most built-in steps can use placeholders in their arguments, those will be automatically resolved from the ```session```:

- URL
- Expected body
- HTTP params (name and value)
- HTTP headers (name and value)
- JSON Path

```tut:silent
import com.github.agourlay.cornichon.CornichonFeature

class PlaceholderFeature extends CornichonFeature {

  def feature =
  Feature("Placeholders examples") {

    Scenario("abstract favorite superheroes") {

      Given I save("favorite-superhero" → "Batman")

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

- ```<random-uuid>``` for a random UUID
- ```<random-positive-integer>``` for a random Integer between 0-10000
- ```<random-string>``` for a random String of length 5
- ```<random-alphanum-string>``` for a random alphanumeric String of length 5
- ```<random-boolean>``` for a random Boolean string
- ```<random-timestamp>``` for a random timestamp
- ```<current-timestamp>``` for the current timestamp

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

- ```*is-present*``` : checks if the field is defined
- ```*any-string*``` : checks if the field is a String
- ```*any-array*``` : checks if the field is an Array
- ```*any-object*``` : checks if the field is an Object
- ```*any-integer*``` : checks if the field is an Integer
- ```*any-positive-integer*``` : checks if the field is a positive Integer
- ```*any-negative-integer*``` : checks if the field is a negative Integer
- ```*any-uuid*``` : checks if the field is a valid UUID
- ```*any-boolean*``` : checks if the field is a boolean
- ```*any-alphanum-string*``` : checks if the field is an alpha-numeric String
- ```*any-date*``` : checks if the field is a 'yyyy-MM-dd' date
- ```*any-date-time*``` : checks if the field is a 'yyyy-MM-dd'T'HH:mm:ss.SSS'Z'' datetime
- ```*any-time*``` : checks if the field is a 'HH:mm:ss.SSS' time"

This feature is still fresh and under experimentation therefore it comes with a couple of limitations:
- it is not yet possible to register custom JSON matchers
- matchers are not supported for JSON arrays assertions
