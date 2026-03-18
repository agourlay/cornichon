{%
laika.title = Cornichon
%}

## Quick Example

```scala
import com.github.agourlay.cornichon.CornichonFeature

class SuperHeroesFeature extends CornichonFeature {

  def feature = Feature("Superheroes API") {

    Scenario("retrieve a superhero") {

      When I get("/superheroes/Batman")

      Then assert status.is(200)

      Then assert body.is("""
      {
        "name": "Batman",
        "realName": "Bruce Wayne",
        "city": "Gotham city",
        "publisher": {
          "name": "DC",
          "foundationYear": 1934
        }
      }
      """)

      Then assert body.path("publisher.name").is("DC")

      Then assert body.path("publisher.foundationYear").isGreaterThan(1900)
    }
  }
}
```
