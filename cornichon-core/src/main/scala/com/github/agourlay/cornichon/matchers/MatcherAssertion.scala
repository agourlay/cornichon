package com.github.agourlay.cornichon.matchers

import cats.syntax.validated._
import cats.data.ValidatedNel
import com.github.agourlay.cornichon.steps.regular.assertStep.Assertion
import cats.syntax.either._
import com.github.agourlay.cornichon.core.{ CornichonError, Done }
import com.github.agourlay.cornichon.core.Done._
import com.github.agourlay.cornichon.json.JsonPath
import io.circe.Json

trait MatcherAssertion extends Assertion {
  val negate: Boolean
  val m: Matcher
  val input: Json

  lazy val validated =
    Either.catchNonFatal(m.predicate(input))
      .leftMap(e ⇒ MatcherAssertionEvaluationError(m, input, e))
      .fold[ValidatedNel[CornichonError, Done]](
        errors ⇒ errors.invalidNel,
        matcherResult ⇒
          // XNOR condition for not negate
          if (matcherResult == !negate) validDone else MatcherAssertionError(m, input, negate).invalidNel
      )
}

case class MatcherAssertionEvaluationError(m: Matcher, input: Json, error: Throwable) extends CornichonError {
  val baseErrorMessage = s"evaluation of matcher '${m.key}' (${m.description}) failed for input '${input.spaces2}'"
  override val causedBy = CornichonError.fromThrowable(error) :: Nil
}

case class MatcherAssertionError(m: Matcher, input: Json, negate: Boolean) extends CornichonError {
  val baseErrorMessage = s"matcher '${m.key}' (${m.description}) ${if (negate) "was expected to fail" else "failed"} for input '${input.spaces2}'"
}

object MatcherAssertion {
  def atJsonPath(jsonPath: JsonPath, json: Json, matcher: Matcher, negateMatcher: Boolean) =
    new MatcherAssertion {
      val negate = negateMatcher
      val m = matcher
      val input = jsonPath.run(json)
    }
}
