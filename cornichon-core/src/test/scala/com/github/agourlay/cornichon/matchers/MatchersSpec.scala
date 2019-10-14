package com.github.agourlay.cornichon.matchers

import java.time.format.DateTimeFormatter

import com.github.agourlay.cornichon.matchers.Matchers._
import io.circe.Json
import org.scalatest._

class MatchersSpec extends WordSpec with Matchers {

  "any-date-time" must {
    "correct in parallel" in {
      1.to(64)
        .map(_ ⇒ MatchersProperties.reasonablyRandomInstantGen.sample)
        .par
        .foreach { instant ⇒
          anyDateTime.predicate(Json.fromString(DateTimeFormatter.ISO_INSTANT.format(instant.get))) should be(true)
        }
    }
  }
}
