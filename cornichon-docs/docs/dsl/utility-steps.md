{%
laika.title = Utility steps
%}

# Utility steps

- pause execution for a given duration

```scala
wait(500.millis)
```

## Debug steps

- showing session and response state for debugging purposes

```scala
 And I show_session                // print all session key-value pairs

 And I show_last_response          // print status, headers, and body of the last response

 And I show_last_response_json     // same as above with pretty-printed JSON body

 And I show_last_status            // print the last response status code

 And I show_last_body              // print the last response body as-is

 And I show_last_body_json         // print the last response body as pretty-printed JSON

 And I show_last_headers           // print the last response headers
```

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
