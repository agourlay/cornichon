package com.github.agourlay.cornichon.framework.examples

import com.github.agourlay.cornichon.CornichonFeature
import com.github.agourlay.cornichon.steps.regular.assertStep.{ AssertStep, Assertion, GenericEqualityAssertion }

import scala.concurrent.duration._

//see  http://deckofcardsapi.com/
class DeckOfCard extends CornichonFeature with DeckSteps {

  override lazy val baseUrl = "http://deckofcardsapi.com/api"

  def feature =
    Feature("Deck of Card API") {

      Scenario("draw any king") {

        Given I get("/deck/new/shuffle/").withParams(
          "deck_count" -> "1"
        )

        Then assert status.is(200)

        And assert body.ignoring("deck_id").is(
          """
          {
            "success": true,
            "shuffled": true,
            "remaining": 52
          }
          """
        )

        And I save_body_path("deck_id" -> "deck-id")

        Eventually(maxDuration = 10.seconds, interval = 10.millis) {

          When I get("/deck/<deck-id>/draw/")

          And assert status.is(200)

          Then assert body.path("cards[0].value").is("KING")

        }
      }

      Scenario("partial deck") {

        Given I get("/deck/new/shuffle/").withParams(
          "cards" -> "AS,2S,KS,AD,2D,KD,AC,2C,KC,AH,2H,KH"
        )

        Then assert status.is(200)

        And assert body.ignoring("deck_id").is(
          """
          {
            "success": true,
            "shuffled": true,
            "remaining": 12
          }
          """
        )

        And I save_body_path("deck_id" -> "deck-id")

        Repeat(6) {

          When I get("/deck/<deck-id>/draw/").withParams(
            "count" -> "2"
          )

          And assert status.is(200)

          And assert body.path("success").is(true)

          Then assert body.path("cards").asArray.not_contains("QH")

        }

      }

      Scenario("test simplified blackjack scoring") {

        WithDataInputs(
          """
            | c1     | c2      | score |
            | "1"    | "3"     |   4   |
            | "1"    | "KING"  |   11  |
            | "JACK" | "QUEEN" |   20  |
            | "ACE"  | "KING"  |   21  |
          """
        ) {
            Then assert verify_hand_score
          }
      }

      Scenario("draw simplified blackjack hand") {

        Given I get("/deck/new/shuffle/").withParams(
          "deck_count" -> "8"
        )

        And assert body.ignoring("deck_id").is(
          """
          {
            "success": true,
            "shuffled": true,
            "remaining": 416
          }
          """
        )

        And I save_body_path("deck_id" -> "deck-id")

        Eventually(maxDuration = 20.seconds, interval = 10.millis) {

          When I get("/deck/<deck-id>/draw/").withParams(
            "count" -> "2"
          )

          And assert body.path("success").is(true)

          And assert body.path("cards").asArray.hasSize(2)

          Then I save_body_path("cards[0].value" -> "c1", "cards[1].value" -> "c2")

          Then `assert the current hand` is_blackjack

        }
      }
    }
}

trait DeckSteps {
  this: CornichonFeature =>

  def verify_hand_score = AssertStep(
    title = "value of 'c1' with 'c2' is 'score'",
    action = sc => Assertion.either {
      for {
        score <- sc.session.get("score").map(_.toInt)
        c1 <- sc.session.get("c1")
        c2 <- sc.session.get("c2")
      } yield GenericEqualityAssertion(score, scoreBlackjackHand(c1, c2))
    }
  )

  def is_blackjack = AssertStep(
    title = "current hand is Blackjack!",
    action = sc => Assertion.either {
      for {
        c1 <- sc.session.get("c1")
        c2 <- sc.session.get("c2")
      } yield GenericEqualityAssertion(21, scoreBlackjackHand(c1, c2))
    }
  )

  def scoreBlackjackHand(c1: String, c2: String): Int = scoreCards(c1) + scoreCards(c2)

  // Yes I know "ACE" is 11 or 1 but we are having a simplified example for fun :)
  def scoreCards(c: String): Int = c match {
    case "1"     => 1
    case "2"     => 2
    case "3"     => 3
    case "4"     => 4
    case "5"     => 5
    case "6"     => 6
    case "7"     => 7
    case "8"     => 8
    case "9"     => 9
    case "10"    => 10
    case "JACK"  => 10
    case "QUEEN" => 10
    case "KING"  => 10
    case "ACE"   => 11
  }
}
