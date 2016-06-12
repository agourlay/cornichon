package com.github.agourlay.cornichon.examples

import com.github.agourlay.cornichon.CornichonFeature
import scala.concurrent.duration._

//see  http://deckofcardsapi.com/
class DeckOfCard extends CornichonFeature {

  override lazy val baseUrl = "http://deckofcardsapi.com/api"

  def feature =
    Feature("Deck of Card API") {

      Scenario("draw any king") {

        Given I get("/deck/new/shuffle/?deck_count=1").withParams(
          "deck_count" -> "1"
        )

        Then assert status.is(200)

        And I save_body_path("deck_id" -> "deck-id")

        Eventually(maxDuration = 10.seconds, interval = 10.millis) {

          When I get("/deck/<deck-id>/draw/")

          And assert status.is(200)

          Then assert body.path("cards[0].value").is("KING")

        }
      }

      // Idea: implement blackjack game :)
    }
}
