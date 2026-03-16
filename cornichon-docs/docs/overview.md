{%
laika.title = Overview
%}

[![Maven Central](https://img.shields.io/maven-central/v/com.github.agourlay/cornichon-core_3)](https://central.sonatype.com/artifact/com.github.agourlay/cornichon-core_3)
[![License](http://img.shields.io/:license-Apache%202-brightgreen.svg)](http://www.apache.org/licenses/LICENSE-2.0.txt)

## How it works

Cornichon tests are built around three core concepts: **steps**, **session**, and **placeholders**.

Steps are the building blocks of a scenario — each step either performs a side effect (like an HTTP request) or asserts an expected state. When a step produces a result, it is stored in the **session**, a key-value store that lives for the duration of a scenario. Values from the session can then be injected into later steps using **placeholders** like `<my-key>`, which are automatically resolved at runtime.

This simple model makes it natural to chain steps together: make a request, save part of the response, and use it in the next request — all without writing boilerplate code.

## Quick example

Find below an example of testing the Open Movie Database API.

```scala mdoc:silent
import com.github.agourlay.cornichon.CornichonFeature

class ReadmeExample extends CornichonFeature {

  def feature = Feature("OpenMovieDatabase API"){

    Scenario("list GOT season 1 episodes"){

      When I get("http://www.omdbapi.com").withParams(
        "t" -> "Game of Thrones",
        "Season" -> "1"
      )

      Then assert status.is(200)

      And assert body.ignoring("Episodes", "Response").is(
        """
        {
          "Title": "Game of Thrones",
          "Season": "1"
        }
        """)

      And assert body.path("Episodes").is(
        """
        |                Title                    |   Released   | Episode | imdbRating |   imdbID    |
        | "Winter Is Coming"                      | "2011-04-17" |   "1"   |    "8.1"   | "tt1480055" |
        | "The Kingsroad"                         | "2011-04-24" |   "2"   |    "7.8"   | "tt1668746" |
        | "Lord Snow"                             | "2011-05-01" |   "3"   |    "7.6"   | "tt1829962" |
        | "Cripples, Bastards, and Broken Things" | "2011-05-08" |   "4"   |    "7.7"   | "tt1829963" |
        | "The Wolf and the Lion"                 | "2011-05-15" |   "5"   |    "8.0"   | "tt1829964" |
        | "A Golden Crown"                        | "2011-05-22" |   "6"   |    "8.1"   | "tt1837862" |
        | "You Win or You Die"                    | "2011-05-29" |   "7"   |    "8.1"   | "tt1837863" |
        | "The Pointy End"                        | "2011-06-05" |   "8"   |    "7.9"   | "tt1837864" |
        | "Baelor"                                | "2011-06-12" |   "9"   |    "8.6"   | "tt1851398" |
        | "Fire and Blood"                        | "2011-06-19" |  "10"   |    "8.4"   | "tt1851397" |
        """)

      And assert body.path("Episodes").asArray.hasSize(10)

      And assert body.path("Episodes[0]").is(
        """
        {
          "Title": "Winter Is Coming",
          "Released": "2011-04-17",
          "Episode": "1",
          "imdbRating": "8.1",
          "imdbID": "tt1480055"
        }
        """)

      And assert body.path("Episodes[0].Released").is("2011-04-17")

      And assert body.path("Episodes[*].Released").is(
        """
        [
          "2011-04-17", "2011-04-24", "2011-05-01", "2011-05-08", "2011-05-15",
          "2011-05-22", "2011-05-29", "2011-06-05", "2011-06-12", "2011-06-19"
        ]
        """)

      And assert body.path("Episodes").asArray.contains(
        """
        {
          "Title": "Winter Is Coming",
          "Released": "2011-04-17",
          "Episode": "1",
          "imdbRating": "8.1",
          "imdbID": "tt1480055"
        }
        """)
    }
  }
}
```

For more examples see the following files which are part of the test pipeline:

- [Embedded Superheroes API](https://github.com/agourlay/cornichon/blob/master/cornichon-test-framework/src/test/scala/com/github/agourlay/cornichon/framework/examples/superHeroes/SuperHeroesScenario.scala).

- [OpenMovieDatabase API](https://github.com/agourlay/cornichon/blob/master/cornichon-test-framework/src/it/scala/com/github/agourlay/cornichon/framework/examples/OpenMovieDatabase.scala).

- [DeckOfCard API](https://github.com/agourlay/cornichon/blob/master/cornichon-test-framework/src/it/scala/com/github/agourlay/cornichon/framework/examples/DeckOfCard.scala).

- [Star Wars API](https://github.com/agourlay/cornichon/blob/master/cornichon-test-framework/src/it/scala/com/github/agourlay/cornichon/framework/examples/StarWars.scala).

- [Math Operations](https://github.com/agourlay/cornichon/blob/master/cornichon-test-framework/src/test/scala/com/github/agourlay/cornichon/framework/examples/math/MathScenario.scala).

And if you enjoy slides, you might like [this presentation](https://speakerdeck.com/agourlay/cornichon-a-scala-dsl-for-testing-http-json-api) given at the [Berlin Scala User Group](https://www.meetup.com/Scala-Berlin-Brandenburg/events/235779912/) which gives more context regarding the creation and usage of this library.

Ready to get started? Head over to [Installation](installation.md) and then [Basics](basics.md).
