## Quick start

cornichon is cross-built for Scala 2.11, and 2.12 so you can just add the following to your build:

``` scala
libraryDependencies += "com.github.agourlay" %% "cornichon" % "0.12.4" % Test
```

Cornichon is currently integrated with [ScalaTest](http://www.scalatest.org/), place your ```Feature``` files inside ```src/test/scala``` and run them using ```sbt test```.

A ```Feature``` is a class extending ```CornichonFeature``` and implementing the required ```feature``` function.

How does it look like?

Find below an example of testing the Open Movie Database API.

```scala
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

- [Embedded Superheroes API](https://github.com/agourlay/cornichon/blob/master/cornichon-scalatest/src/test/scala/com/github/agourlay/cornichon/examples/superHeroes/SuperHeroesScenario.scala).

- [OpenMovieDatabase API](https://github.com/agourlay/cornichon/blob/master/cornichon-scalatest/src/it/scala/com.github.agourlay.cornichon.examples/OpenMovieDatabase.scala).

- [DeckOfCard API](https://github.com/agourlay/cornichon/blob/master/cornichon-scalatest/src/it/scala/com.github.agourlay.cornichon.examples/DeckOfCard.scala).

- [Star Wars API](https://github.com/agourlay/cornichon/blob/master/cornichon-scalatest/src/it/scala/com.github.agourlay.cornichon.examples/StarWars.scala).

- [Math Operations](https://github.com/agourlay/cornichon/blob/master/cornichon-scalatest/src/test/scala/com/github/agourlay/cornichon/examples/math/MathScenario.scala).

And if you enjoy slides, you might like [this presentation](https://speakerdeck.com/agourlay/cornichon-a-scala-dsl-for-testing-http-json-api) given at the [Berlin Scala User Group](https://www.meetup.com/Scala-Berlin-Brandenburg/events/235779912/) which gives more context regarding the creation and usage of this library.

## Structure

A Cornichon test is the definition of a so-called ```feature```.

A ```feature``` can have several ```scenarios``` which in turn can have several ```steps```.

The example below contains one ```feature``` with one ```scenario``` with two ```steps```.

```scala
import com.github.agourlay.cornichon.CornichonFeature

class CornichonExamplesSpec extends CornichonFeature {

  def feature = Feature("Checking google"){

    Scenario("Google is up and running"){

      When I get("http://google.com")

      Then assert status.is(302)
    }
  }
}
```

The failure modes are the following:


- A ```feature``` fails if one or more ```scenarios``` fail.

- A ```scenario``` fails if at least one ```step``` fails.

- A ```scenario``` will stop at the first failed step encountered and ignore the remaining ```steps```.

