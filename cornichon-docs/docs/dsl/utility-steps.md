{%
laika.title = Utility steps
%}

# Utility steps

- pause execution for a given duration

```scala
wait(500.millis)
```

## Debug steps

- showing session content for debugging purpose

```scala
 And I show_session

 And I show_last_response

 And I show_last_response_json (pretty print the json body)

 And I show_last_status

 And I show_last_body

 And I show_last_body_json (pretty print the json body)

 And I show_last_headers
```


Those descriptions might be already outdated, in case of doubt always refer to those [examples](https://github.com/agourlay/cornichon/blob/master/cornichon-test-framework/src/test/scala/com/github/agourlay/cornichon/framework/examples/superHeroes/SuperHeroesScenario.scala) as they are executed as part of Cornichon's test suite.

## DSL composition

Series of steps defined with Cornichon's DSL can be reused within different `Scenarios`.

Using the keyword `Attach` if the series starts with a `Step` and without if it starts with a wrapping block.

```scala mdoc:silent
import com.github.agourlay.cornichon.CornichonFeature
import scala.concurrent.duration._

class CompositionFeature extends CornichonFeature {

  def feature =
    Feature("Cornichon feature example") {

      Scenario("demonstrate DSL composition") {

        Then assert superhero_exists("batman")

        Then assert random_superheroes_until("Batman")

      }
    }

  def superhero_exists(name: String) =
    Attach {
      When I get(s"/superheroes/$name").withParams("sessionId" -> "<session-id>")
      Then assert status.is(200)
    }

  def random_superheroes_until(name: String) =
    Eventually(maxDuration = 3.seconds, interval = 10.milliseconds) {
      When I get("/superheroes/random").withParams("sessionId" -> "<session-id>")
      Then assert body.path("name").is(name)
      Then I print_step("bingo!")
    }
}
```

It is possible to give a title to an attached block using `AttachAs(title)`.
